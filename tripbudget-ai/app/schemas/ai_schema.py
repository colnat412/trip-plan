from pydantic import BaseModel, Field
from typing import List, Optional

# Format response schema for AI-generated trip plan
class ActivitySchema(BaseModel):
    title: str = Field(description="Tên hoạt động ngắn gọn")
    startTime: str = Field(description="Thời gian bắt đầu, format HH:MM (VD: 08:00)")
    endTime: str = Field(description="Thời gian kết thúc, format HH:MM (VD: 10:30)")
    location: Optional[str] = Field(description="Tên địa điểm cụ thể nếu có")
    category: str = Field(description="CHỈ ĐƯỢC CHỌN 1 TRONG CÁC GIÁ TRỊ: FOOD_BEVERAGE, TRANSPORTATION, SHOPPING, ACCOMMODATION, SIGHTSEEING, OTHER")
    estimatedCost: float = Field(description="Chi phí ước tính bằng VNĐ (VD: 150000)")
    note: Optional[str] = Field(description="Mẹo hoặc lưu ý nhỏ (Tối đa 1 câu)")

class PlanDaySchema(BaseModel):
    dayNumber: int = Field(description="Ngày thứ mấy của chuyến đi (1, 2, 3...)")
    activities: List[ActivitySchema] = Field(description="Danh sách các hoạt động trong ngày được sắp xếp theo thời gian")

class TripPlanResponseSchema(BaseModel):
    days: List[PlanDaySchema]

# API request schema from user input
class GeneratePlanRequest(BaseModel):
    destination: str
    days: int
    budget: float
    people: int
    preferences: Optional[str] = ""
