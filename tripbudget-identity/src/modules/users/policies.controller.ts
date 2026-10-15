import { Body, Controller, Post, Req, ValidationPipe } from '@nestjs/common';
import type { Request } from 'express';
import { AllowPendingPolicies } from '../auth/decorators/allow-pending-policies.decorator';
import { AcceptPoliciesDto } from './dtos/accept-policies.dto';
import { UsersService } from './users.service';
import { HashidsService } from 'src/common/hashids/hashids.service';
import { policyProfile } from './policies';

@Controller('users/me/policies')
export class PoliciesController {
  constructor(
    private readonly users: UsersService,
    private readonly hashids: HashidsService,
  ) {}

  @AllowPendingPolicies()
  @Post('accept')
  async accept(
    @Req() request: Request & { user: { sub: string | number } },
    @Body(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    )
    dto: AcceptPoliciesDto,
  ) {
    const user = await this.users.acceptPolicies(request.user.sub, dto);
    return {
      id: this.hashids.encode(user.id),
      email: user.email,
      name: user.name,
      avatarUrl: user.avatarUrl,
      ...policyProfile(user),
    };
  }
}
