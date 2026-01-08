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

### 🚗 Speed-Dependent Volume (SDV) - ĐỘC QUYỀN
Tính năng cao cấp thường chỉ có trên các dòng xe sang (Mercedes, BMW, Audi):

- **Nguyên lý**: Khi xe chạy nhanh (tiếng ồn lốp, gió tăng), App tự động tăng âm lượng. Khi xe dừng đèn đỏ, âm lượng tự giảm về mức êm dịu.
- **Triển khai**: Sử dụng GPS của Android Box để lấy vận tốc (Speed) theo thời gian thực.
- **3 mức độ nhạy**:

| Mức độ | Tốc độ mỗi +5% boost | Mô tả |
|--------|---------------------|-------|
| 🔵 **Low** | 30 km/h | Xe cách âm tốt |
| 🟢 **Mid** | 20 km/h | Xe phổ thông (Mặc định) |
| 🔴 **High** | 10 km/h | Xe ồn, cửa kính mỏng |

> **Lưu ý**: Tính năng này yêu cầu quyền truy cập Vị trí (GPS). Âm lượng tối đa được boost thêm 20%.

## 🚀 Cài đặt & Sử dụng

1. **Build APK**: Mở dự án trong Android Studio và chọn **Build > Build APK(s)**.
2. **Cài đặt**: Copy file APK vào Android Box/Màn hình xe và cài đặt.
3. **Cấp quyền**:
   - Lần đầu kích hoạt Nút nổi, bạn cần cấp quyền **"Display over other apps"** (Hiển thị trên ứng dụng khác).
   - Cấp quyền **Notification** (Thông báo) để widget hoạt động ổn định không bị hệ thống tắt.
   - Cấp quyền **Location** (Vị trí) nếu sử dụng tính năng SDV.

## 🛠️ Tùy chỉnh (Settings)

Bấm vào biểu tượng ⚙️ trên màn hình chính để truy cập menu cài đặt chuyên nghiệp:

1.  **Audio Curve Profile**:
    *   **Linear (1.0)**: Tăng đều.
    *   **Balanced (2.0)**: Cân bằng (Mặc định).
    *   **Deep (3.0)**: Tăng chậm ở mức nhỏ (cho loa công suất lớn).
2.  **Quick Panel Timeout**: Thời gian tự ẩn thanh trượt nhanh (3s, 5s, 10s).
3.  **Widget Opacity**: Chỉnh độ mờ của nút nổi (20% - 100%) để không che khuất màn hình.
4.  **Speed-Dependent Volume (SDV)**: 
    *   Bật/Tắt tính năng tự động điều chỉnh âm lượng theo tốc độ.
    *   Chọn mức độ nhạy: Low / Mid / High.

## ⚠️ Yêu cầu hệ thống
- **Android SDK tối thiểu**: API 21 (Android 5.0 Lollipop)
- **Android SDK mục tiêu**: API 34 (Android 14)
- **Màn hình**: Tối ưu cho chế độ màn hình ngang (Landscape)
- **GPS**: Cần có GPS để sử dụng tính năng Speed-Dependent Volume

## 🔑 Quyền ứng dụng
| Quyền | Mục đích |
|-------|----------|
| `SYSTEM_ALERT_WINDOW` | Hiển thị nút nổi trên các ứng dụng khác |
| `FOREGROUND_SERVICE` | Duy trì widget và SDV hoạt động ổn định |
| `ACCESS_FINE_LOCATION` | Lấy tốc độ GPS cho tính năng SDV |
| `POST_NOTIFICATIONS` | Hiển thị thông báo điều khiển |

## 🤝 Mã nguồn
Được viết bằng **Kotlin** thuần và Android XML Views. Không sử dụng thư viện nặng bên ngoài, đảm bảo hiệu năng tối đa.
