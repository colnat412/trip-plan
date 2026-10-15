import {
  Body,
  Controller,
  Get,
  Post,
  Req,
  Res,
  UnauthorizedException,
} from '@nestjs/common';
import { AuthService } from './auth.service';
import {
  GoogleLoginDto,
  RegisterDto,
  ResendOtpDto,
  VerifyOtpDto,
} from './dtos/auto.dto';
import type { Request, Response } from 'express';
import { ConfigService } from '@nestjs/config';
import { Public } from './decorators/public.decorator';
import { AllowPendingPolicies } from './decorators/allow-pending-policies.decorator';

@Controller('auth')
@AllowPendingPolicies()
export class AuthController {
  constructor(
    private readonly authService: AuthService,
    private readonly config: ConfigService,
  ) {}

  @Public()
  @Post('verify-otp')
  async verifyOtp(
    @Body() dto: VerifyOtpDto,
    @Res({ passthrough: true }) response: Response,
  ) {
    try {
      const result = await this.authService.verifyOtp(dto);
      this.setRefreshCookie(response, result.refreshToken);
      return { accessToken: result.accessToken, user: result.user };
    } catch (error) {
      console.log('Error', error);
      throw error;
    }
  }

  @Public()
  @Post('resend-otp')
  async resendOtp(@Body() dto: ResendOtpDto) {
    try {
      return this.authService.resendOtp(dto);
    } catch (error) {
      console.log('Error', error);
      throw error;
    }
  }

  @Public()
  @Post('google')
  async loginWithGoogle(
    @Body() dto: GoogleLoginDto,
    @Res({ passthrough: true }) response: Response,
  ) {
    const result = await this.authService.loginWithGoogle(dto);
    this.setRefreshCookie(response, result.refreshToken);
    return { accessToken: result.accessToken, user: result.user };
  }

  @Public()
  @Post('register')
  async register(@Body() dto: RegisterDto) {
    try {
      return this.authService.register(dto);
    } catch (error) {
      console.log('Error', error);
      throw error;
    }
  }

  @Public()
  @Post('login')
  async login(
    @Body() dto: RegisterDto,
    @Res({ passthrough: true }) response: Response,
  ) {
    try {
      const { accessToken, refreshToken, user } =
        await this.authService.login(dto);
      this.setRefreshCookie(response, refreshToken);
      return { accessToken, user };
    } catch (error) {
      console.log('Error', error);
      throw error;
    }
  }

  @Get('me')
  async getMe(@Req() request: any) {
    try {
      return await this.authService.getProfile(request.user.sub);
    } catch (error) {
      console.log('Error', error);
      throw error;
    }
  }

  @Post('logout')
  async logout(
    @Req() request: any,
    @Res({ passthrough: true }) response: Response,
  ) {
    try {
      await this.authService.logout(request.user.sub, request.user.sid);
      response.clearCookie('refresh_token', {
        path: '/api/auth',
      });
    } catch (error) {
      console.log('Error', error);
      throw error;
    }
  }

  @Public()
  @Post('refresh')
  async refresh(
    @Req() request: Request,
    @Res({ passthrough: true }) response: Response,
  ) {
    const refreshToken = request.cookies?.refresh_token;

    if (!refreshToken) {
      throw new UnauthorizedException('Refresh token is missing');
    }

    const result = await this.authService.refresh(refreshToken);

    this.setRefreshCookie(response, result.refreshToken);

    return { accessToken: result.accessToken };
  }

  // @Public()
  @Get('test')
  async get() {
    return 'GET TEST GUARD';
  }

  private setRefreshCookie(response: Response, refreshToken: string) {
    response.cookie('refresh_token', refreshToken, {
      httpOnly: true,
      secure: process.env.COOKIE_SECURE === 'true',
      sameSite: 'lax',
      path: '/api/auth',
      maxAge: Number(process.env.REFRESH_TOKEN_TTL_SECONDS) * 1000,
    });
  }
}
