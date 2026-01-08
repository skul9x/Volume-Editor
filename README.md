# 🔊 VolumeEditor - Ứng dụng điều chỉnh âm lượng xe hơi cao cấp

**VolumeEditor** là ứng dụng điều chỉnh âm lượng độ chính xác cao được thiết kế chuyên biệt cho Màn hình Android xe hơi (Head Units) và Android Box. Giải quyết vấn đề "bước nhảy âm lượng" quá lớn của hệ thống bằng cách cung cấp thanh trượt 100 bước với thuật toán đường cong logarithmic phù hợp với thính giác con người.

## ✨ Tính năng nổi bật

### 🎚️ Điều khiển âm thanh chính xác
- **Thanh trượt 100 bước**: Tinh chỉnh chi tiết (0-100%) so với 15 bước mặc định của Android.
- **Đường cong Logarithmic**: Tăng âm lượng chậm ở mức nhỏ và nhanh hơn ở mức lớn, giúp cảm nhận âm thanh tự nhiên hơn.
- **Tích hợp hệ thống**: Can thiệp trực tiếp vào `AudioManager` (STREAM_MUSIC) của Android.

### 🎨 Giao diện "Automotive Cyber-Glass"
- **Thiết kế cao cấp**: Giao diện tối (Dark Mode) độ tương phản cao, tối ưu cho lái xe ban đêm.
- **Glassmorphism**: Các khung điều khiển dạng kính mờ sang trọng với hiệu ứng phát sáng Neon.
- **Màu Neon chủ đạo**: Cyan và Pink nổi bật, dễ nhìn.
- **Tối ưu cảm ứng**: Nút bấm khổng lồ (120x100dp) và chữ số lớn (96sp) giúp thao tác dễ dàng khi đang lái xe.

### 🔴 Nút nổi (Floating Widget)
- **Luôn hiển thị**: Điều chỉnh âm lượng từ mọi màn hình (Google Maps, Youtube...).
- **Cử chỉ thông minh**:
  - 👆 **Chạm 1 lần**: Tắt/Bật tiếng (Mute/Unmute) ngay lập tức.
  - 👆👆 **Chạm 2 lần**: Mở ứng dụng chính.
  - 👆⏱️ **Giữ (Long Press)**: Hiện thanh trượt nhanh (tự động ẩn sau 5s).

## 🚀 Cài đặt & Sử dụng

1. **Build APK**: Mở dự án trong Android Studio và chọn **Build > Build APK(s)**.
2. **Cài đặt**: Copy file APK vào Android Box/Màn hình xe và cài đặt.
3. **Cấp quyền**:
   - Lần đầu kích hoạt Nút nổi, bạn cần cấp quyền **"Display over other apps"** (Hiển thị trên ứng dụng khác).
   - Cấp quyền **Notification** (Thông báo) để widget hoạt động ổn định không bị hệ thống tắt.

## 🛠️ Tùy chỉnh nâng cao

Bạn có thể chỉnh độ cong của âm lượng trong file `MainActivity.kt`:

```kotlin
// Hệ số quyết định độ cong của biểu đồ âm lượng
// 1.0 = Tuyến tính (Mặc định)
// 2.0 = Logarithmic (Khuyên dùng cho Âm thanh)
// 3.0 = Cong nhiều (Tăng rất chậm ở đoạn đầu)
// Thay đổi giá trị này để phù hợp với loa của xe bạn
private val curveExponent = 2.0
```

## ⚠️ Yêu cầu hệ thống
- **Android SDK tối thiểu**: API 21 (Android 5.0 Lollipop)
- **Android SDK mục tiêu**: API 34 (Android 14)
- **Màn hình**: Tối ưu cho chế độ màn hình ngang (Landscape)

## 🤝 Mã nguồn
Được viết bằng **Kotlin** thuần và Android XML Views. Không sử dụng thư viện nặng bên ngoài, đảm bảo hiệu năng tối đa.
