import {
  ConflictException,
  Injectable,
  NotFoundException,
  UnauthorizedException,
} from '@nestjs/common';
import { DataSource } from 'typeorm';
import { HashidsService } from 'src/common/hashids/hashids.service';
import { UploadStatusEnum, UploadTypeEnum } from 'src/config/enums/upload.enum';
import { UploadEntity } from '../uploads/entities/upload.entity';
import { UserEntity } from './entities/user.entity';
import { policyProfile } from './policies';

@Injectable()
export class UserAvatarService {
  constructor(
    private readonly database: DataSource,
    private readonly hashids: HashidsService,
  ) {}

  async setAvatar(subject: string | number, uploadId: string | null) {
    const userId = this.hashids.decode(subject);
    if (!userId || !Number.isSafeInteger(userId) || userId < 1) {
      throw new UnauthorizedException('Invalid user');
    }

    return this.database.transaction(async (manager) => {
      const user = await manager.findOne(UserEntity, {
        where: { id: userId },
        lock: { mode: 'pessimistic_write' },
      });
      if (!user) throw new UnauthorizedException('User not found');

      let upload: UploadEntity | null = null;
      if (uploadId !== null) {
        upload = await manager.findOne(UploadEntity, {
          where: {
            id: uploadId,
            userId: user.id,
            type: UploadTypeEnum.PROFILE_AVATAR,
          },
          lock: { mode: 'pessimistic_write' },
        });
        if (!upload) throw new NotFoundException('Avatar upload not found');
        if (
          user.avatarUploadId === upload.id &&
          upload.status === UploadStatusEnum.ATTACHED
        ) {
          return this.profile(user);
        }
        if (upload.status !== UploadStatusEnum.PENDING) {
          throw new ConflictException('Upload is no longer available');
        }
      }

      const oldUploadId = user.avatarUploadId;
      user.avatarUploadId = upload?.id ?? null;
      user.avatarUrl = upload?.url ?? null;
      await manager.update(UserEntity, user.id, {
        avatarUploadId: user.avatarUploadId,
        avatarUrl: user.avatarUrl,
      });

      if (upload) {
        await manager.update(UploadEntity, upload.id, {
          status: UploadStatusEnum.ATTACHED,
        });
      }
      if (oldUploadId && oldUploadId !== upload?.id) {
        await manager.update(
          UploadEntity,
          {
            id: oldUploadId,
            userId: user.id,
            type: UploadTypeEnum.PROFILE_AVATAR,
            status: UploadStatusEnum.ATTACHED,
          },
          { status: UploadStatusEnum.DELETE_PENDING },
        );
      }
      return this.profile(user);
    });
  }

  private profile(user: UserEntity) {
    return {
      id: this.hashids.encode(user.id),
      email: user.email,
      name: user.name,
      avatarUrl: user.avatarUrl,
      ...policyProfile(user),
    };
  }
}
