import {
  Body,
  Controller,
  Delete,
  Patch,
  Req,
  ValidationPipe,
} from '@nestjs/common';
import type { Request } from 'express';
import { UserAvatarService } from './user-avatar.service';
import { UpdateAvatarDto } from './dtos/update-avatar.dto';
import { UpdateProfileDto } from './dtos/update-profile.dto';
import { UsersService } from './users.service';

type AuthenticatedRequest = Request & { user: { sub: string | number } };

@Controller('users/me')
export class UserController {
  constructor(
    private readonly avatars: UserAvatarService,
    private readonly users: UsersService,
  ) {}

  @Patch('profile')
  updateProfile(
    @Req() request: AuthenticatedRequest,
    @Body(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    )
    dto: UpdateProfileDto,
  ) {
    return this.users.updateProfile(request.user.sub, dto.name);
  }

  @Patch('avatar')
  updateAvatar(
    @Req() request: AuthenticatedRequest,
    @Body(
      new ValidationPipe({
        whitelist: true,
        forbidNonWhitelisted: true,
        transform: true,
      }),
    )
    dto: UpdateAvatarDto,
  ) {
    return this.avatars.setAvatar(request.user.sub, dto.uploadId);
  }

  @Delete('avatar')
  removeAvatar(@Req() request: AuthenticatedRequest) {
    return this.avatars.setAvatar(request.user.sub, null);
  }
}
