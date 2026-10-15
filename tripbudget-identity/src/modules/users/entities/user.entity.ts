import { UserStatusEnum } from 'src/config/enums/user.enum';
import { Column, Entity, PrimaryGeneratedColumn } from 'typeorm';

@Entity('users')
export class UserEntity {
  @PrimaryGeneratedColumn({ type: 'bigint' })
  id: number;

  @Column()
  email: string;

  @Column({ name: 'password', nullable: true })
  password: string;

  @Column()
  name: string;

  @Column({ name: 'avatar_url', type: 'varchar', nullable: true })
  avatarUrl: string | null;

  @Column({ name: 'avatar_upload_id', type: 'uuid', nullable: true })
  avatarUploadId: string | null;

  @Column({ name: 'policies_accepted_at', type: 'timestamptz', nullable: true })
  policiesAcceptedAt: Date | null;

  @Column({
    name: 'policies_version',
    type: 'varchar',
    length: 32,
    nullable: true,
  })
  policiesVersion: string | null;

  @Column({
    name: 'policies_locale',
    type: 'varchar',
    length: 2,
    nullable: true,
  })
  policiesLocale: string | null;

  @Column()
  status: UserStatusEnum;
}
