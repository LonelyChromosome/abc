# BPA Parking Lab — nguồn cập nhật trực tiếp (15/10/2026)

## Bằng chứng từ APK Phenikaa Uni 2.0.24
- APK dạng split có libapp.so của Flutter, chứa gate_api_service.dart.
- Có host https://gate-statistic-prod.phx-smartuni.com (chưa xác minh host ứng với từng endpoint).
- Có các đường dẫn truy vấn:
  - /app/v1/even-scan-data/get-all
  - /app/v1/even-scan-data/get-detail
  - /app/v1/parking-vehicle-in-school/get-all
- Có Firebase Messaging và mã sự kiện ACTION_CHECK_IN_PARING / ACTION_CHECK_OUT_PARING.
- Phần lịch sử quẹt và thông báo là hai luồng dữ liệu khác nhau. Chưa đo được thời gian server ghi lịch sử.
- Manifest không xuất bản gate ContentProvider, service hay receiver cho ứng dụng ngoài. Không có đường nghe chung push với BPA được chứng minh.
- Có middleware access token / refresh token; cách cấp quyền cho ứng dụng thứ ba chưa được xác minh.

## Quan sát
Một lần quẹt thẻ ở 13:08 có thông báo xuất hiện khoảng 13:10. Chứng minh **trong lần thử đó** notification đến trễ, không chứng minh chậm ở server, FCM hay Android.

## Tiến độ
- [x] Tìm API lịch sử quẹt thẻ và trạng thái xe trong APK.
- [x] Tìm cơ chế Firebase và đường giao tiếp app-to-app.
- [x] Xây bộ phát hiện event mới độc lập với notification: baseline lịch sử, loại bỏ event cũ, chống trùng, chỉ ENTER, đo source lag.
- [x] Chạy smoke test Kotlin trên máy build.
- [ ] Chưa biết cấu trúc request/response chính xác, mức quyền, và API cập nhật nhanh hơn notification hay không.
- [ ] Chưa kết nối máy chủ, chưa có tự kích hoạt GPS bằng event từ server.
- [ ] Chưa có chứng cứ server cho phép bên thứ ba đăng ký push riêng.

## Phép thử để chốt bài toán
Dùng quyền đọc lịch sử của chính tài khoản qua giao thức được phép: đo T0 quẹt cổng, T1 API lần đầu chứa sự kiện, T2 điện thoại nhận thông báo. Nếu T1 << T2 thì xác nhận API sớm hơn push. Nếu T1 cũng chậm, vấn đề ở đường ghi dữ liệu phía backend. Tránh polling nền liên tục; nếu server hỗ trợ kênh push chính thức thì ưu tiên kênh đó.

## Nguyên tắc
Không gọi server trong giai đoạn phân tích APK, không dùng token/phiên được trích từ sandbox riêng của Phenikaa Uni, không giả mạo device, không lách cơ chế integrity. Nhánh nghiên cứu riêng, không sửa bản GPS ở main.
