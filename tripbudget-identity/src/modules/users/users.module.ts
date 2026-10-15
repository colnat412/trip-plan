import { UserController } from './user.controller';
import { UserAvatarService } from './user-avatar.service';
import { HashidsModule } from 'src/common/hashids/hashids.module';
import { UploadEntity } from '../uploads/entities/upload.entity';
import { Module } from '@nestjs/common';
import { UsersService } from './users.service';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UserEntity } from './entities/user.entity';
import { PoliciesController } from './policies.controller';

@Module({
  imports: [
    TypeOrmModule.forFeature([UserEntity, UploadEntity]),
    HashidsModule,
  ],
  controllers: [UserController, PoliciesController],
  providers: [UsersService, UserAvatarService],
  exports: [UsersService],
})
export class UsersModule {}
