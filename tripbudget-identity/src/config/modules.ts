import { AuthModule } from 'src/modules/auth/auth.module';
import { TestModule } from 'src/modules/test/test.module';
import { UploadsModule } from 'src/modules/uploads/uploads.module';
import { UsersModule } from 'src/modules/users/users.module';

export const AppModules = [UsersModule, AuthModule, UploadsModule, TestModule];
