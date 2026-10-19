import { Injectable, Logger, UnauthorizedException } from '@nestjs/common';
import { InjectRepository } from '@nestjs/typeorm';
import { Repository } from 'typeorm';
import { CloudinaryService } from './cloudinary.service';
import { UploadStatusEnum, UploadTypeEnum } from 'src/config/enums/upload.enum';
import { HashidsService } from 'src/common/hashids/hashids.service';
import { UsersService } from '../users/users.service';
import { UploadEntity } from './entities/upload.entity';
import { randomUUID } from 'node:crypto';

const UPLOAD_PREFIX: Record<UploadTypeEnum, string> = {
  [UploadTypeEnum.PROFILE_AVATAR]: 'tripbudget/profile-avatars',
  [UploadTypeEnum.TRIP_COVER]: 'tripbudget/trip-covers',
  [UploadTypeEnum.EXPENSE_RECEIPT]: 'tripbudget/expense-receipts',
};

@Injectable()
export class UploadsService {
  private readonly logger = new Logger(UploadsService.name);

  constructor(
    private readonly cloudinaryService: CloudinaryService,
    @InjectRepository(UploadEntity)
    private readonly uploads: Repository<UploadEntity>,
    private readonly hashids: HashidsService,
    private readonly users: UsersService,
  ) {}

  async uploadImage(
    file: Express.Multer.File,
    purpose: UploadTypeEnum,
    subject: string | number,
  ) {
    const userId = this.hashids.decode(subject);
    if (!userId || !Number.isSafeInteger(userId) || userId < 1) {
      throw new UnauthorizedException('Invalid user');
    }
    const user = await this.users.findByCondition({ id: userId });
    if (!user) throw new UnauthorizedException('User not found');

    const uploadId = randomUUID();
    const publicId = `${UPLOAD_PREFIX[purpose]}/${uploadId}`;
    const result = await this.cloudinaryService.uploadImage(
      file.buffer,
      publicId,
    );

    let saved: UploadEntity;
    try {
      saved = await this.uploads.save(
        this.uploads.create({
          id: uploadId,
          userId: user.id,
          type: purpose,
          publicId: result.public_id,
          url: result.secure_url,
          resourceType: result.resource_type,
          format: result.format,
          bytes: result.bytes,
          width: result.width,
          height: result.height,
          status: UploadStatusEnum.PENDING,
        }),
      );
    } catch (error) {
      try {
        await this.cloudinaryService.deleteImage(result.public_id);
      } catch {
        // Database is unavailable: retain the identifier for manual reconciliation.
        this.logger.error(
          `Upload cleanup failed; reconciliation required: uploadId=${uploadId}, publicId=${result.public_id}`,
        );
      }
      throw error;
    }

    return {
      uploadId: saved.id,
      publicId: saved.publicId,
      url: saved.url,
      resourceType: saved.resourceType,
      format: saved.format,
      bytes: saved.bytes,
      width: saved.width,
      height: saved.height,
      purpose: saved.type,
      status: saved.status,
    };
  }
}
