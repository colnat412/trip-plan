import { ExecutionContext, Injectable } from '@nestjs/common';
import { Reflector } from '@nestjs/core';
import { AuthGuard } from '@nestjs/passport';
import { IS_PUBLIC_KEY } from '../decorators/public.decorator';
import { ALLOW_PENDING_POLICIES } from '../decorators/allow-pending-policies.decorator';
import { UsersService } from '../../users/users.service';

@Injectable()
export class JwtAuthGuard extends AuthGuard('jwt') {
  constructor(
    private readonly reflector: Reflector,
    private readonly users: UsersService,
  ) {
    super();
  }

  async canActivate(context: ExecutionContext) {
    const isPublic = this.reflector.getAllAndOverride<boolean>(IS_PUBLIC_KEY, [
      context.getHandler(), // @Public() method
      context.getClass(), // @Public() controller
    ]);

    if (isPublic) {
      return true;
    }

    // If the route is not public, proceed with the default JWT authentication
    const authenticated = await super.canActivate(context);
    if (!authenticated) return false;
    // check allow policies
    const isAllowPolicy = this.reflector.getAllAndOverride<boolean>(
      ALLOW_PENDING_POLICIES,
      [context.getHandler(), context.getClass()],
    );
    if (!isAllowPolicy) {
      const request = context
        .switchToHttp()
        .getRequest<{ user: { sub: string | number } }>();
      await this.users.assertPoliciesAccepted(request.user.sub);
    }
    // pass all
    return true;
  }
}
