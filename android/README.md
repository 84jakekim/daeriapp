# GPS 시뮬레이터 (Mock Location App)

대리운전 앱 개발/테스트용 안드로이드 모의 위치(Mock Location) 앱.

- 카카오 지도에서 원하는 위치를 탭하거나 주소/장소명으로 검색
- 시스템 GPS / Network 프로바이더에 가짜 좌표를 주입 (Foreground Service)
- 다른 앱(내 드라이버 앱 포함)에서 `LocationManager` / `FusedLocationProvider`로 위치를 가져갈 때 가짜 위치가 노출됨

> **반드시 본인이 개발/테스트하는 앱에 대해서만 사용하세요.** 상용 서비스(카카오T대리, 카카오T, 쿠팡이츠, 배달의민족, 게임 등)에 사용 시 약관 위반·법적 문제가 발생할 수 있습니다.

---

## 빌드/실행 절차

### 1) 사전 준비
- Android Studio Hedgehog (2023.1) 이상
- JDK 17 (Android Studio에 내장)
- 실제 안드로이드 단말기 (에뮬레이터는 mock location app 지정 자체가 안 됨)

### 2) 카카오 키 발급
1. https://developers.kakao.com/ 가입/로그인
2. **내 애플리케이션** → 애플리케이션 추가
3. **앱 키** 에서 다음을 복사:
    - `네이티브 앱 키` → `KAKAO_NATIVE_APP_KEY`
    - `REST API 키` → `KAKAO_REST_API_KEY` (검색 기능용)
4. **플랫폼 → Android 플랫폼 등록**
    - 패키지명: `com.daeri.gpsspoofer`
    - 키 해시: 디버그 빌드의 키 해시 등록
      ```bash
      keytool -exportcert -alias androiddebugkey -keystore ~/.android/debug.keystore \
        | openssl sha1 -binary | openssl base64
      ```

### 3) 키 설정
`android/local.properties.example` 를 `android/local.properties` 로 복사 후 키 입력:
```
KAKAO_NATIVE_APP_KEY=발급받은_네이티브앱키
KAKAO_REST_API_KEY=발급받은_REST_API_키
```

### 4) 빌드

**Android Studio에서 열기 (권장)**: `File → Open → android/` 디렉토리 선택. Gradle Wrapper가 자동 생성됩니다.

**CLI**: Gradle Wrapper (`gradlew`)가 없으면 먼저 생성:
```bash
cd android
gradle wrapper --gradle-version=8.5    # 시스템에 gradle 설치되어 있어야 함
./gradlew assembleDebug
```

생성된 APK 경로: `android/app/build/outputs/apk/debug/app-debug.apk`

### 5) 단말기 설정 (필수)
1. **설정 → 휴대전화 정보 → 빌드번호 7회 탭** → 개발자 모드 활성화
2. **설정 → 시스템 → 개발자 옵션**
3. **"모의 위치 앱 선택"** → **GPS 시뮬레이터** 선택
4. 앱 실행 → 위치 권한 허용
5. 지도에서 위치 선택 → **모의 위치 시작**

---

## 주의 사항

- **FusedLocationProvider 한정**: 일부 앱(특히 Google Play Services 기반)은 `isMock` 플래그를 검사해서 모의 위치를 무시할 수 있습니다.
- **안티치트 우회 용도 아님**: 게임/배달 앱 등의 안티치트는 mock location app 지정 여부, `Location.isMock`, 디바이스 무결성(Play Integrity) 등 다중 검사를 수행합니다.
- **Android 14+**: 포그라운드 서비스 위치 타입 권한이 강화됨. 본 앱은 `FOREGROUND_SERVICE_LOCATION` 권한을 선언함.
- **본인 앱 테스트 외 용도 금지**: 위치 기반 서비스의 약관과 한국 정보통신망법 등을 위반할 수 있습니다.

---

## 구조

```
android/
├── app/
│   ├── build.gradle.kts
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/daeri/gpsspoofer/
│       │   ├── SpooferApp.kt           # Application — Kakao SDK init
│       │   ├── MainActivity.kt          # 지도 UI, 검색, Start/Stop
│       │   ├── MockLocationService.kt   # ForegroundService — 좌표 주입
│       │   └── KakaoSearchClient.kt     # Kakao Local REST API
│       └── res/...
├── build.gradle.kts
├── settings.gradle.kts
├── gradle.properties
└── local.properties.example
```
