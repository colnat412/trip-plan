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

type AuthenticatedRequest = Request & { user: { sub: string | number } };

@Controller('users/me/avatar')
export class UserController {
  constructor(private readonly avatars: UserAvatarService) {}

  @Patch()
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

  @Delete()
  removeAvatar(@Req() request: AuthenticatedRequest) {
    return this.avatars.setAvatar(request.user.sub, null);
  }
}
