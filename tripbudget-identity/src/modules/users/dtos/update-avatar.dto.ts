import { IsUUID } from 'class-validator';

export class UpdateAvatarDto {
  @IsUUID('4')
  uploadId: string;
}
