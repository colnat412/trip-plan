import {
  ConflictException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import * as bcrypt from 'bcrypt';
import { UserStatusEnum } from 'src/config/enums/user.enum';
import { UsersService } from '../users/users.service';
import {
  GoogleLoginDto,
  LoginDto,
  RegisterDto,
  ResendOtpDto,
  SessionData,
  VerifyOtpDto,
} from './dtos/auto.dto';
import { createHmac, randomBytes, randomUUID, timingSafeEqual } from 'crypto';
import { ConfigService } from '@nestjs/config';
import { RedisService } from 'src/common/redis/redis.service';
import { JwtService } from '@nestjs/jwt';

import { HashidsService } from '../../common/hashids/hashids.service';
import { policyProfile } from '../users/policies';
import { MailerService } from 'src/common/mailer/mailer.service';
import { OAuth2Client } from 'google-auth-library';

@Injectable()
export class AuthService {
  private readonly refreshTtlSeconds = 60 * 60 * 24 * 30; // 30 days
  constructor(
    private readonly usersService: UsersService,
    private readonly config: ConfigService,
    private readonly redis: RedisService,
    private readonly jwtService: JwtService,
    private readonly hashidsService: HashidsService,
    private readonly mailerService: MailerService,
  ) {}

  async register(dto: RegisterDto) {
    const email = dto.email.trim().toLowerCase();

    let user = await this.usersService.findByCondition({ email });

    if (user && user.status === UserStatusEnum.ACTIVE) {
      throw new ConflictException('Email already exists');
    }

    const passwordHash = await bcrypt.hash(dto.password, 12);

    if (!user) {
      user = await this.usersService.create({
        email,
        name: dto.name.trim(),
        password: passwordHash,
        status: UserStatusEnum.INACTIVE,
      });
    } else {
      user.name = dto.name.trim();
      user.password = passwordHash;
      await this.usersService.save(user);
    }

    const otp = Math.floor(100000 + Math.random() * 900000).toString();

    await this.redis.redisClient.set(`otp:verify:${email}`, otp, 'EX', 300);

    await this.mailerService.sendOtpEmail(email, otp, user.name);

    return {
      success: true,
      email: user.email,
      message: 'OTP has been sent to your email.',
    };
  }

  async verifyOtp(dto: VerifyOtpDto) {
    try {
      const email = dto.email.trim().toLowerCase();
      const cachedOtp = await this.redis.redisClient.get(`otp:verify:${email}`);
      if (!cachedOtp || cachedOtp !== dto.otp) {
        throw new UnauthorizedException('Invalid OTP');
      }

      const user = await this.usersService.findByCondition({ email });

      if (!user) {
        throw new UnauthorizedException('User not found');
      }

      user.status = UserStatusEnum.ACTIVE;
      await this.usersService.save(user);

      await this.redis.redisClient.del(`otp:verify:${email}`);

      const session = await this.createSession(user.id);
      return {
        ...session,
        user: {
          id: this.hashidsService.encode(user.id),
          email: user.email,
          name: user.name,
          avatarUrl: user.avatarUrl,
          ...policyProfile(user),
        },
      };
    } catch (error) {
      console.log('Verify OTP Error', error);
      throw new UnauthorizedException('Invalid OTP');
    }
  }

  async resendOtp(dto: ResendOtpDto) {
    try {
      const email = dto.email.trim().toLowerCase();

      const user = await this.usersService.findByCondition({ email });

      if (!user) {
        throw new UnauthorizedException('User not found');
      }

      if (user.status === UserStatusEnum.ACTIVE) {
        throw new ConflictException('Account is already active');
      }

      const otp = Math.floor(100000 + Math.random() * 900000).toString();
      await this.redis.redisClient.set(`otp:verify:${email}`, otp, 'EX', 300);

      await this.mailerService.sendOtpEmail(email, otp, user.name);

      return {
        success: true,
        email: user.email,
        message: 'OTP has been resent to your email.',
      };
    } catch (error) {
      console.log('Resend OTP Error', error);
      throw new UnauthorizedException('Failed to resend OTP');
    }
  }

  async login(dto: LoginDto) {
    try {
      const email = dto.email.trim().toLowerCase();

      const user = await this.usersService.findByCondition({ email });

      if (!user) {
        throw new UnauthorizedException('Email or password is not valid');
      }

      const passwordMatched = await bcrypt.compare(dto.password, user.password);

      if (!passwordMatched) {
        throw new UnauthorizedException('Email or password is not valid');
      }

      if (user.status !== UserStatusEnum.ACTIVE) {
        throw new UnauthorizedException('Account is not active');
      }

      const session = await this.createSession(user.id);

      return {
        ...session,
        user: {
          id: this.hashidsService.encode(user.id),
          email: user.email,
          name: user.name,
          avatarUrl: user.avatarUrl,
          ...policyProfile(user),
        },
      };
    } catch (error) {
      console.log('Login error', error);
      throw new UnauthorizedException('Email or password is not valid');
    }
  }

  async loginWithGoogle(dto: GoogleLoginDto) {
    try {
      let payload:
        { email?: string; name?: string; picture?: string } | undefined;

      const isIdToken =
        dto.credential.includes('.') && dto.credential.split('.').length === 3;

      if (isIdToken) {
        const client = new OAuth2Client(process.env.GOOGLE_CLIENT_ID);

        const ticket = await client.verifyIdToken({
          idToken: dto.credential,
          audience: process.env.GOOGLE_CLIENT_ID,
        });

        payload = ticket.getPayload();
      } else {
        const response = await fetch(
          'https://www.googleapis.com/oauth2/v3/userinfo',
          {
            headers: {
              Authorization: `Bearer ${dto.credential}`,
            },
          },
        );

        if (!response.ok) {
          throw new UnauthorizedException('Invalid Google access token');
        }

        payload = (await response.json()) as {
          email?: string;
          name?: string;
          picture?: string;
        };
      }

      if (!payload || !payload.email) {
        throw new UnauthorizedException('Invalid Google credential');
      }
      const email = payload.email.toLowerCase();

      let user = await this.usersService.findByCondition({ email });

      if (!user) {
        user = await this.usersService.save({
          email,
          name: payload.name || email.split('@')[0],
          avatarUrl: payload.picture,
          status: UserStatusEnum.ACTIVE,
        });
      } else {
        let isChanged = false;
        if (payload.picture && !user.avatarUrl) {
          user.avatarUrl = payload.picture;
          isChanged = true;
        }
        if (user.status !== UserStatusEnum.ACTIVE) {
          user.status = UserStatusEnum.ACTIVE;
          isChanged = true;
        }
        if (isChanged) {
          await this.usersService.save(user);
        }
      }

      const session = await this.createSession(user.id);
      return {
        ...session,
        user: {
          id: this.hashidsService.encode(user.id),
          email: user.email,
          name: user.name,
          avatarUrl: user.avatarUrl,
          ...policyProfile(user),
        },
      };
    } catch (error) {
      console.log('Google login error', error);
      throw new UnauthorizedException('Failed to login with Google');
    }
  }

  async getProfile(userId: string | number) {
    const rawId =
      typeof userId === 'number'
        ? userId
        : (this.hashidsService.decode(userId) ?? Number(userId));

    const user = await this.usersService.findByCondition({
      id: rawId,
    });

    if (!user) {
      throw new UnauthorizedException('User not found');
    }

    return {
      id: this.hashidsService.encode(user.id),
      email: user.email,
      name: user.name,
      avatarUrl: user.avatarUrl,
      ...policyProfile(user),
    };
  }

  async logout(userId: string, sessionId: string) {
    try {
      await this.revokeSession(userId, sessionId);
    } catch (error) {
      console.log('Logout Error', error);
      throw new UnauthorizedException('Invalid session');
    }
  }

  async refresh(refreshToken: string) {
    const parts = refreshToken.split('.');

    // Check format {sessionId}.{randomSecret}
    if (parts.length !== 2) {
      throw new UnauthorizedException('Invalid refresh token');
    }

    const [sessionId, secret] = parts;

    if (!sessionId || !secret) {
      throw new UnauthorizedException('Invalid refresh token');
    }

    const sessionKey = this.getSessionKey(sessionId);

    const sessionRaw = await this.redis.redisClient.get(sessionKey);

    // Redis key not found: session expired or revoked.
    if (!sessionRaw) {
      throw new UnauthorizedException('Session expired or revoked');
    }

    const session = JSON.parse(sessionRaw) as SessionData;

    // hash random secret from refresh token and compare with hash stored in session.
    const incomingHash = this.hashRefreshSecret(secret);

    if (!this.safeHashEquals(incomingHash, session.refreshTokenHash)) {
      // revoke if use old refresh token or refresh token is tampered.
      await this.revokeSession(session.userId, sessionId);

      throw new UnauthorizedException('Invalid refresh token');
    }

    return this.rotateSession(sessionId, session);
  }

  private async rotateSession(sessionId: string, session: SessionData) {
    const remainingSeconds = Math.floor(
      (new Date(session.expiresAt).getTime() - Date.now()) / 1000,
    );

    if (remainingSeconds <= 0) {
      await this.revokeSession(session.userId, sessionId);

      throw new UnauthorizedException('Session expired');
    }

    const newRefreshSecret = randomBytes(48).toString('base64url');

    session.refreshTokenHash = this.hashRefreshSecret(newRefreshSecret);

    await this.saveSession(sessionId, session, remainingSeconds);

    return {
      accessToken: await this.jwtService.signAsync({
        sub: session.userId,
        sid: sessionId,
        jti: randomUUID(),
        type: 'access',
      }),
      refreshToken: `${sessionId}.${newRefreshSecret}`,
    };
  }

  private safeHashEquals(left: string, right: string) {
    const leftBuffer = Buffer.from(left);
    const rightBuffer = Buffer.from(right);

    return (
      leftBuffer.length === rightBuffer.length &&
      timingSafeEqual(leftBuffer, rightBuffer)
    );
  }

  private async revokeSession(userId: string, sessionId: string) {
    try {
      await this.redis.redisClient
        .multi()
        .del(this.getSessionKey(sessionId))
        .srem(this.getUserSessionsKey(userId), sessionId)
        .exec();
    } catch (error) {
      console.log('Revoke Session Error', error);
      throw new UnauthorizedException('Invalid session');
    }
  }
  private async createSession(userId: string | number) {
    const sessionId = randomUUID();

    const refreshSecret = randomBytes(48).toString('base64url');

    const expiresAt = new Date(
      Date.now() + this.refreshTtlSeconds * 1000,
    ).toISOString();

    const session: SessionData = {
      userId: String(userId),
      refreshTokenHash: this.hashRefreshSecret(refreshSecret),
      createdAt: new Date().toISOString(),
      expiresAt,
    };

    await this.saveSession(sessionId, session, this.refreshTtlSeconds);
    const accessToken = await this.jwtService.signAsync({
      sub: userId,
      sid: sessionId,
      jti: randomUUID(),
      type: 'access',
    });

    return {
      accessToken,
      // Only controller allow to give refreshToken to HttpOnly cookie.
      refreshToken: `${sessionId}.${refreshSecret}`,
    };
  }

  private async saveSession(
    sessionId: string,
    session: SessionData,
    ttlSeconds: number,
  ) {
    const userSessionsKey = this.getUserSessionsKey(session.userId);
    console.log(
      'userSessionsKey',
      userSessionsKey,
      this.getSessionKey(sessionId),
    );

    await this.redis.redisClient
      .multi()
      .set(
        this.getSessionKey(sessionId),
        JSON.stringify(session),
        'EX',
        ttlSeconds,
      )
      .sadd(userSessionsKey, sessionId)
      // .expire(userSessionsKey, ttlSeconds)
      .exec();
  }

  private getSessionKey(sessionId: string) {
    return `auth:session:${sessionId}`;
  }

  private getUserSessionsKey(userId: string) {
    return `auth:user-sessions:${userId}`;
  }

  private hashRefreshSecret(secret: string) {
    return createHmac(
      'sha256',
      process.env.REFRESH_TOKEN_PEPPER || 'default_pepper',
    )
      .update(secret)
      .digest('hex');
  }
}
