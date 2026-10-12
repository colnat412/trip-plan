import { BadGatewayException, Injectable } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import type { UploadApiResponse } from 'cloudinary';
import { v2 as cloudinary } from 'cloudinary';

@Injectable()
export class CloudinaryService {
  constructor(config: ConfigService) {
    cloudinary.config({
      cloud_name: config.getOrThrow<string>('CLOUDINARY_CLOUD_NAME'),
      api_key: config.getOrThrow<string>('CLOUDINARY_API_KEY'),
      api_secret: config.getOrThrow<string>('CLOUDINARY_API_SECRET'),
      secure: true,
    });
  }

  uploadImage(buffer: Buffer, publicId: string): Promise<UploadApiResponse> {
    return new Promise((resolve, reject) => {
      const stream = cloudinary.uploader.upload_stream(
        {
          resource_type: 'image',
          timeout: 60_000,
          public_id: publicId,
          overwrite: false,
          allowed_formats: ['jpg', 'jpeg', 'png', 'webp'],
        },
        (error, result) => {
          if (error || !result) {
            reject(new BadGatewayException('Error upload image'));
            return;
          }

          resolve(result);
        },
      );

      stream.on('error', () => {
        reject(new BadGatewayException('Error to connect Cloudinary'));
      });

      stream.end(buffer);
    });
  }

  async deleteImage(publicId: string): Promise<void> {
    try {
      const result: { result: string } = await cloudinary.uploader.destroy(
        publicId,
        {
          resource_type: 'image',
          invalidate: true,
        },
      );
      if (result.result !== 'ok' && result.result !== 'not found') {
        throw new Error('Unexpected deletion result');
      }
    } catch {
      throw new BadGatewayException('Unable to delete Cloudinary image');
    }
  }
}
