import { Injectable, Logger } from '@nestjs/common';
import { ConfigService } from '@nestjs/config';
import * as nodemailer from 'nodemailer';

@Injectable()
export class MailerService {
  private readonly logger = new Logger(MailerService.name);
  private transporter: nodemailer.Transporter;

  constructor(private readonly config: ConfigService) {
    this.transporter = nodemailer.createTransport({
      host: process.env.MAIL_HOST,
      port: Number(process.env.MAIL_PORT),
      secure: process.env.MAIL_SECURE === 'true',
      auth: {
        user: process.env.MAIL_USER,
        pass: process.env.MAIL_PASSWORD,
      },
    });
  }

  async sendOtpEmail(
    to: string,
    otp: string,
    userName: string,
  ): Promise<boolean> {
    const mailUser = process.env.MAIL_USER;
    const mailPassword = process.env.MAIL_PASSWORD;

    if (!mailUser || !mailPassword) {
      this.logger.warn(
        `[DEV MODE] Skip sending real email because MAIL_USER or MAIL_PASSWORD is not configured. OTP for ${to} is: ${otp}`,
      );
      return true;
    }

    const mailFrom = process.env.MAIL_FROM || `"Vivu" <${mailUser}>`;

    const mailOptions = {
      from: mailFrom,
      to,
      subject: `[Vivu] Mã xác thực tài khoản / Verification Code: ${otp}`,
      html: `
        <!DOCTYPE html>
        <html lang="vi">
        <head>
          <meta charset="utf-8">
          <meta name="viewport" content="width=device-width, initial-scale=1.0">
          <title>Mã xác thực Vivu / Verification Code</title>
        </head>
        <body style="margin: 0; padding: 0; background-color: #F8FAFC; font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, Helvetica, Arial, sans-serif; color: #0F172A; -webkit-font-smoothing: antialiased;">
          <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0" style="background-color: #F8FAFC; padding: 48px 16px;">
            <tr>
              <td align="center">
                <table role="presentation" width="100%" border="0" cellpadding="0" cellspacing="0" style="max-width: 480px; background-color: #FFFFFF; border-radius: 12px; border: 1px solid #E2E8F0; padding: 36px 32px; box-shadow: 0 1px 3px rgba(0, 0, 0, 0.05);">
                  
                  <tr>
                    <td style="padding-bottom: 24px;">
                      <table role="presentation" border="0" cellpadding="0" cellspacing="0">
                        <tr>
                          <td style="width: 32px; height: 32px; background: #1E3A8A; border-radius: 8px; text-align: center; vertical-align: middle;">
                            <span style="color: #FFFFFF; font-size: 16px; font-weight: 800; line-height: 32px;">T</span>
                          </td>
                          <td style="padding-left: 10px; vertical-align: middle;">
                            <span style="font-size: 18px; font-weight: 700; color: #0F172A; letter-spacing: -0.2px;">ViVu</span>
                          </td>
                        </tr>
                      </table>
                    </td>
                  </tr>

                  <tr>
                    <td style="padding-bottom: 12px;">
                      <h1 style="margin: 0; font-size: 20px; font-weight: 600; color: #0F172A; letter-spacing: -0.3px;">
                        Mã xác thực tài khoản
                      </h1>
                      <div style="font-size: 14px; font-weight: 500; color: #64748B; margin-top: 2px;">
                        Account Verification Code
                      </div>
                    </td>
                  </tr>

                  <tr>
                    <td style="padding-bottom: 24px; font-size: 15px; line-height: 1.6; color: #334155;">
                      Xin chào <strong>${userName}</strong>,<br>
                      Vui lòng sử dụng mã bảo mật bên dưới để hoàn tất xác thực tài khoản của bạn trên ViVu.
                      <div style="font-size: 13.5px; color: #64748B; line-height: 1.5; margin-top: 6px;">
                        Please use the verification code below to complete your account setup on ViVu.
                      </div>
                    </td>
                  </tr>

                  <tr>
                    <td style="padding-bottom: 24px;">
                      <div style="background-color: #F8FAFC; border-radius: 8px; padding: 18px 24px; text-align: center; border: 1px solid #E2E8F0;">
                        <div style="font-family: 'SF Mono', SFMono-Regular, Consolas, 'Liberation Mono', Menlo, Courier, monospace; font-size: 32px; font-weight: 700; letter-spacing: 8px; color: #1E3A8A; padding-left: 8px;">
                          ${otp}
                        </div>
                        <div style="font-size: 12px; color: #64748B; margin-top: 6px;">
                          Hiệu lực trong 5 phút • Valid for 5 minutes
                        </div>
                      </div>
                    </td>
                  </tr>

                  <tr>
                    <td style="padding-bottom: 28px; font-size: 13px; line-height: 1.6; color: #64748B;">
                      <div>Vì lý do an toàn, không chia sẻ mã này cho bất kỳ ai. Nếu bạn không yêu cầu mã này, vui lòng bỏ qua email.</div>
                      <div style="font-size: 12px; color: #94A3B8; margin-top: 4px;">For security reasons, do not share this code with anyone. If you didn't make this request, you can safely ignore this email.</div>
                    </td>
                  </tr>

                  <tr>
                    <td style="border-top: 1px solid #F1F5F9; padding-top: 18px; font-size: 12px; color: #94A3B8; line-height: 1.5;">
                      <div>TripBudget • Hệ thống quản lý ngân sách và kế hoạch chuyến đi</div>
                      <div style="font-size: 11px; color: #CBD5E1; margin-top: 2px;">Smart Travel & Expense Management System</div>
                    </td>
                  </tr>

                </table>
              </td>
            </tr>
          </table>
        </body>
        </html>
      `,
    };

    try {
      await this.transporter.sendMail(mailOptions);
      this.logger.log(`[SUCCESS] OTP email sent successfully to ${to}`);
      return true;
    } catch (error) {
      this.logger.error(`Failed to send email to ${to}: ${error}`);
      return false;
    }
  }
}
