import { Equals, IsIn } from 'class-validator';
import { POLICY_VERSION } from '../policies';

export class AcceptPoliciesDto {
  @Equals(true)
  termsAccepted: boolean;

  @Equals(true)
  contentAccepted: boolean;

  @Equals(true)
  privacyAccepted: boolean;

  @Equals(POLICY_VERSION)
  version: string;

  @IsIn(['vi', 'en'])
  locale: string;
}
