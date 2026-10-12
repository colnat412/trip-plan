import { UsersModule } from '../users/users.module';
import { HashidsModule } from 'src/common/hashids/hashids.module';
import { Module } from '@nestjs/common';
import { UploadsService } from './uploads.service';
import { UploadsController } from './uploads.controller';
import { CloudinaryService } from './cloudinary.service';
import { TypeOrmModule } from '@nestjs/typeorm';
import { UploadEntity } from './entities/upload.entity';

@Module({
  imports: [
    TypeOrmModule.forFeature([UploadEntity]),
    UsersModule,
    HashidsModule,
  ],
  controllers: [UploadsController],
  providers: [UploadsService, CloudinaryService],
})
export class UploadsModule {}
