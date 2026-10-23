import {
  BadRequestException,
  ForbiddenException,
  Injectable,
  UnauthorizedException,
} from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { IsNull, Repository } from 'typeorm';
import { HashidsService } from 'src/common/hashids/hashids.service';
import { AcceptPoliciesDto } from './dtos/accept-policies.dto';
import { POLICY_VERSION, policyProfile } from './policies';
import { UserEntity } from './entities/user.entity';
import { RegisterDto } from '../auth/dtos/auto.dto';

@Injectable()
export class UsersService {
  constructor(
    @InjectRepository(UserEntity)
    private readonly userRepository: Repository<UserEntity>,
    private readonly hashids: HashidsService,
  ) {}

  private async authenticatedUser(subject: string | number) {
    const id = this.hashids.decode(subject);
    if (!id || !Number.isSafeInteger(id))
      throw new UnauthorizedException('Invalid user');
    const user = await this.userRepository.findOneBy({ id });
    if (!user) throw new UnauthorizedException('User not found');
    return user;
  }

  async assertPoliciesAccepted(subject: string | number) {
    const user = await this.authenticatedUser(subject);
    if (!user.policiesAcceptedAt || !user.policiesVersion) {
      throw new ForbiddenException({
        statusCode: 403,
        code: 'POLICY_ACCEPTANCE_REQUIRED',
        message: 'Accept the policies before using Trip Plan',
      });
    }
  }

  async updateProfile(subject: string | number, name: string) {
    const user = await this.authenticatedUser(subject);
    await this.userRepository.update(user.id, { name });
    const updated = await this.authenticatedUser(subject);
    return {
      id: this.hashids.encode(updated.id),
      email: updated.email,
      name: updated.name,
      avatarUrl: updated.avatarUrl,
      ...policyProfile(updated),
    };
  }

  async acceptPolicies(subject: string | number, dto: AcceptPoliciesDto) {
    if (
      dto.termsAccepted !== true ||
      dto.contentAccepted !== true ||
      dto.privacyAccepted !== true ||
      dto.version !== POLICY_VERSION ||
      !['vi', 'en'].includes(dto.locale)
    ) {
      throw new BadRequestException('All current policies must be accepted');
    }
    const user = await this.authenticatedUser(subject);
    await this.userRepository.update(
      { id: user.id, policiesAcceptedAt: IsNull() },
      {
        policiesAcceptedAt: new Date(),
        policiesVersion: POLICY_VERSION,
        policiesLocale: dto.locale,
      },
    );
    return (await this.userRepository.findOneBy({ id: user.id }))!;
  }

  async findByCondition(
    condition: Record<string, any>,
  ): Promise<UserEntity | null> {
    try {
      return this.userRepository.findOne({ where: condition });
    } catch (error) {
      throw new Error(
        `Failed to find user by ${JSON.stringify(condition)}: ${error}`,
      );
    }
  }

  async create(dto: RegisterDto): Promise<UserEntity> {
    const { email, password, name, status } = dto;
    try {
      const user = this.userRepository.create({
        email,
        password,
        name,
        status,
      });
      return this.userRepository.save(user);
    } catch (error) {
      throw new Error(`Failed to create user: ${error}`);
    }
  }

  async save(user: Partial<UserEntity>): Promise<UserEntity> {
    try {
      return this.userRepository.save(user);
    } catch (error) {
      throw new Error(`Failed to save user: ${error}`);
    }
  }
}
