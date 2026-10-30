import base64
import os
import json
import requests
import time
from app.schemas.ai_schema import GeneratePlanRequest


from dotenv import load_dotenv

class AIService:
    def __init__(self):
        load_dotenv()
        self.api_key = os.getenv("GEMINI_API_KEY", "").strip()

    def list_available_models(self):
        if not self.api_key:
            raise ValueError("Missing GEMINI_API_KEY")
        
        url = f"https://generativelanguage.googleapis.com/v1beta/models?key={self.api_key}"
        resp = requests.get(url)
        if resp.status_code != 200:
            return {"error": resp.json()}
        return {"models": [m["name"] for m in resp.json().get("models", [])]}

    def generate_trip_plan(self, req: GeneratePlanRequest):
        if not self.api_key:
            raise ValueError("Missing GEMINI_API_KEY")

        prompt = f"""
        Bạn là một chuyên gia thiết kế lịch trình du lịch tối ưu chi phí.
        Hãy lập lịch trình chi tiết cho chuyến đi đến {req.destination} trong {req.days} ngày cho {req.people} người.
        Tổng ngân sách dự kiến (cho tất cả mọi người): {req.budget:,.0f} VNĐ.
        Yêu cầu bổ sung từ người dùng: {req.preferences if req.preferences else "Không có"}

        Lưu ý QUAN TRỌNG:
        - Phân bổ chi phí hợp lý để tổng estimatedCost của tất cả hoạt động không vượt quá tổng ngân sách.
        - category CHỈ ĐƯỢC CHỌN 1 TRONG: FOOD_BEVERAGE, TRANSPORTATION, SHOPPING, ACCOMMODATION, SIGHTSEEING, OTHER.
        - Trả về 100% bằng tiếng Việt.
        - BẠN PHẢI TRẢ VỀ ĐÚNG FORMAT JSON DƯỚI ĐÂY, KHÔNG ĐƯỢC CÓ TEXT NÀO KHÁC BÊN NGOÀI:
        """

        prompt += """
        {
          "days": [
            {
              "dayNumber": 1,
              "activities": [
                {
                  "title": "Tên hoạt động",
                  "startTime": "08:00",
                  "endTime": "10:00",
                  "location": "Địa điểm",
                  "category": "SIGHTSEEING",
                  "estimatedCost": 150000,
                  "note": "Ghi chú ngắn"
                }
              ]
            }
          ]
        }
        """

        candidate_models = [
            "models/gemini-flash-lite-latest",
            "models/gemini-3.5-flash-lite",
            "models/gemini-3.1-flash-lite",
            "models/gemini-flash-latest",
            "models/gemini-3.8-flash"
        ]
        
        payload = {
            "contents": [{"parts": [{"text": prompt}]}],
            "generationConfig": {
                "temperature": 0.7,
                "responseMimeType": "application/json"
            }
        }
        headers = {"Content-Type": "application/json"}
        
        last_error = None
        for model in candidate_models:
            url = f"https://generativelanguage.googleapis.com/v1beta/{model}:generateContent?key={self.api_key}"
            
            resp = requests.post(url, json=payload, headers=headers)
            resp_data = resp.json()
            
            if resp.status_code == 200:
                raw_text = resp_data["candidates"][0]["content"]["parts"][0]["text"]
                return json.loads(raw_text)
                
            elif resp.status_code == 503:
                time.sleep(2)
                resp = requests.post(url, json=payload, headers=headers)
                if resp.status_code == 200:
                    raw_text = resp.json()["candidates"][0]["content"]["parts"][0]["text"]
                    return json.loads(raw_text)
                resp_data = resp.json()

            last_error = resp_data
            continue
            
        raise Exception(f"Đã thử toàn bộ danh sách model Gemini nhưng đều thất bại! Lỗi cuối: {last_error}")

    ALLOWED_IMAGE_TYPES = {"image/jpeg", "image/png", "image/webp", "image/heic", "application/pdf"}
    MAX_IMAGE_SIZE_BYTES = 10 * 1024 * 1024  # 10MB

    def scan_receipt_image(self, image_bytes: bytes, mime_type: str = "image/jpeg"):
        if not self.api_key:
            raise ValueError("Missing GEMINI_API_KEY")
        if not mime_type or mime_type.lower() not in self.ALLOWED_IMAGE_TYPES:
            raise ValueError(f"Unsupported image type: {mime_type}. Allowed types: {self.ALLOWED_IMAGE_TYPES}")
        if len(image_bytes) > self.MAX_IMAGE_SIZE_BYTES:
            raise ValueError(f"Image size exceeds the maximum limit of {self.MAX_IMAGE_SIZE_BYTES / (1024 * 1024)} MB")
        
        image_base64 = base64.b64encode(image_bytes).decode("utf-8")
        prompt = """
        Bạn là một chuyên gia OCR tài chính chuyên trích xuất hóa đơn du lịch, nhà hàng, khách sạn và biên lai thanh toán.
        Nhiệm vụ: Hãy phân tích hình ảnh hóa đơn/biên lai được cung cấp và trích xuất các thông tin sau:
        - merchant_name: Tên quán ăn, cửa hàng, tài xế hoặc nhà cung cấp dịch vụ (tiếng Việt có dấu).
        - amount: Tổng số tiền thanh toán thực tế (dạng số float, ví dụ 150000, tuyệt đối không chứa chữ đ hay dấu chấm phân tách).
        - currency: Đơn vị tiền tệ (VND, USD, EUR...). Mặc định là "VND" nếu hóa đơn tại Việt Nam.
        - expense_date: Ngày in trên hóa đơn theo format YYYY-MM-DD. Nếu không thấy năm, lấy năm hiện tại 2026. Nếu không có ngày, để null.
        - category: BẮT BUỘC chọn 1 trong các giá trị:
            + FOOD_BEVERAGE: Nhà hàng, ăn uống, cafe, siêu thị thực phẩm.
            + TRANSPORTATION: Taxi, xe ôm, grab, vé tàu, vé xe, máy bay, tiền xăng.
            + ACCOMMODATION: Khách sạn, homestay, phòng nghỉ.
            + SIGHTSEEING: Vé tham quan, vé vào cửa, vé sự kiện, tour.
            + SHOPPING: Quần áo, đồ lưu niệm, đồ điện tử.
            + OTHER: Các loại khác, bao gồm vui chơi và giải trí không thuộc vé.
        - confidence: Ước lượng độ chính xác từ 0.0 đến 1.0 (ví dụ ảnh mờ thì 0.6, rõ nét thì 0.95).
        - items: Mảng các món đọc được, mỗi món có { "name": "tên món", "quantity": 1, "price": 50000 }.
        - raw_text: Tóm tắt 1 câu ngắn gọn về hóa đơn này (VD: "Bữa trưa 3 món tại Cơm Tấm Cali").
        YÊU CẦU BẮT BUỘC:
        Chỉ trả về DUY NHẤT một chuỗi JSON hợp lệ, KHÔNG bọc trong markdown ```json, KHÔNG giải thích gì thêm bên ngoài.
        """
        candidate_models = [
            "models/gemini-2.5-flash",
            "models/gemini-flash-lite-latest",
            "models/gemini-3.5-flash-lite",
            "models/gemini-2.5-flash-lite",
            "models/gemini-flash-latest",
        ]
        payload = {
            "contents": [
                {
                    "parts": [
                        {"text": prompt},
                        {
                            "inlineData": {
                                "mimeType": mime_type,
                                "data": image_base64
                            }
                        }
                    ]
                }
            ],
            "generationConfig": {
                "temperature": 0.1,  
                "responseMimeType": "application/json"
            }
        }
        last_error = None
        for model in candidate_models:
            url = f"https://generativelanguage.googleapis.com/v1beta/{model}:generateContent?key={self.api_key}"
            try:
                resp = requests.post(url, json=payload, timeout=25)
                if resp.status_code == 200:
                    data = resp.json()
                    candidates = data.get("candidates", [])
                    if candidates:
                        text_response = candidates[0]["content"]["parts"][0]["text"]
                        clean_text = text_response.strip().replace("```json", "").replace("```", "").strip()
                        return json.loads(clean_text)
                else:
                    last_error = f"{model} error {resp.status_code}: {resp.text}"
            except Exception as e:
                last_error = str(e)
        raise RuntimeError(f"Some error occurred: {last_error}")