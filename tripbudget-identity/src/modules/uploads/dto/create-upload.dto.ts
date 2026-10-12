import { IsEnum } from 'class-validator';
import { UploadTypeEnum } from 'src/config/enums/upload.enum';

export class CreateUploadDto {
  @IsEnum(UploadTypeEnum)
  purpose: UploadTypeEnum;
}
