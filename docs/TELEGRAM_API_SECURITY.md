# 🔐 Bảo Mật Telegram API Credentials

## ⚠️ Cảnh Báo Quan Trọng

**KHÔNG BAO GIỜ** commit `local.properties` lên Git! File này chứa thông tin nhạy cảm và đã được thêm vào `.gitignore`.

## 🛠️ Thiết Lập Cho Developer Mới

### 1. Đăng ký Telegram API

1. Truy cập: https://my.telegram.org/apps
2. Đăng nhập bằng số điện thoại Telegram của bạn
3. Tạo ứng dụng mới và lấy `api_id` và `api_hash`

### 2. Cấu Hình Local

1. Sao chép file template:
   ```bash
   cp local.properties.example local.properties
   ```

2. Mở `local.properties` và thay thế:
   ```properties
   telegram.api.id=YOUR_API_ID_HERE
   telegram.api.hash=YOUR_API_HASH_HERE
   ```

3. Build project:
   ```bash
   ./gradlew clean build
   ```

## 🔍 Cách Hoạt Động

```mermaid
graph LR
    A[local.properties] -->|Đọc bởi| B[build.gradle.kts]
    B -->|Tạo| C[BuildConfig]
    C -->|Sử dụng trong| D[TelegramService.kt]
    
    style A fill:#ff6b6b,color:#fff
    style B fill:#4ecdc4,color:#fff
    style C fill:#95e1d3,color:#000
    style D fill:#f38181,color:#fff
```

1. **`local.properties`**: Chứa credentials (KHÔNG commit)
2. **`build.gradle.kts`**: Đọc values và expose qua `BuildConfig`
3. **`BuildConfig`**: Auto-generated class chứa constants
4. **`TelegramService.kt`**: Sử dụng `BuildConfig.TELEGRAM_API_ID` và `BuildConfig.TELEGRAM_API_HASH`

## ✅ Xác Minh

Kiểm tra xem `local.properties` có bị track bởi Git không:

```bash
git status
```

Nếu thấy `local.properties` trong danh sách, chạy:

```bash
git rm --cached local.properties
echo "/local.properties" >> .gitignore
git add .gitignore
git commit -m "chore: remove sensitive data from git"
```

## 🚀 CI/CD Setup

Đối với GitHub Actions hoặc CI/CD pipelines, thêm secrets vào environment variables:

```yaml
# .github/workflows/build.yml
- name: Create local.properties
  run: |
    echo "telegram.api.id=${{ secrets.TELEGRAM_API_ID }}" >> local.properties
    echo "telegram.api.hash=${{ secrets.TELEGRAM_API_HASH }}" >> local.properties
```

## 📝 References

- [Telegram API Documentation](https://core.telegram.org/api/obtaining_api_id)
- [Android Security Best Practices](https://developer.android.com/privacy-and-security/security-tips)
