import { UploadStatusEnum, UploadTypeEnum } from 'src/config/enums/upload.enum';
import {
  Column,
  CreateDateColumn,
  Entity,
  Index,
  PrimaryGeneratedColumn,
} from 'typeorm';

@Entity({ name: 'uploads', schema: 'public' })
@Index('idx_uploads_user_id', ['userId'])
@Index('idx_uploads_status_created_at', ['status', 'createdAt'])
export class UploadEntity {
  @PrimaryGeneratedColumn('uuid')
  id: string;

  @Column({ name: 'user_id', type: 'bigint' })
  userId: number;

  @Column({ type: 'enum', enum: UploadTypeEnum, enumName: 'upload_type_enum' })
  type: UploadTypeEnum;

  @Column({ name: 'public_id', type: 'text', unique: true })
  publicId: string;

  @Column({ type: 'text' })
  url: string;

  @Column({ name: 'resource_type', type: 'varchar', length: 30 })
  resourceType: string;

  @Column({ type: 'varchar', length: 20 })
  format: string;

  @Column({ type: 'integer' })
  bytes: number;

  @Column({ type: 'integer' })
  width: number;

  @Column({ type: 'integer' })
  height: number;

  @Column({
    type: 'enum',
    enum: UploadStatusEnum,
    enumName: 'upload_status_enum',
    default: UploadStatusEnum.PENDING,
  })
  status: UploadStatusEnum;

  @CreateDateColumn({ name: 'created_at', type: 'timestamptz' })
  createdAt: Date;
}
