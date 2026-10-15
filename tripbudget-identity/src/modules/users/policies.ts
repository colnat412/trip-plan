export const POLICY_VERSION = '2026-10-14';

export function policyProfile(user: {
  policiesAcceptedAt?: Date | null;
  policiesVersion?: string | null;
}) {
  return {
    policiesAcceptedAt: user.policiesAcceptedAt ?? null,
    policiesVersion: user.policiesVersion ?? null,
    requiredPolicyVersion: POLICY_VERSION,
  };
}
