import { SetMetadata } from '@nestjs/common';

export const ALLOW_PENDING_POLICIES = 'allowPendingPolicies';
export const AllowPendingPolicies = () =>
  SetMetadata(ALLOW_PENDING_POLICIES, true);
