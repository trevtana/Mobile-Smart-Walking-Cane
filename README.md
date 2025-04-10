# 🧓🏻💡 Smart Walking Cane – Tongkat Pintar IoT untuk Lansia

Tongkat pintar yang dirancang untuk membantu lansia tetap aman, mandiri, dan terkoneksi. Dilengkapi dengan sistem pelacakan lokasi, tombol darurat (SOS), dan lampu sorot LED yang dapat dikontrol melalui aplikasi mobile.

---

## ✨ Fitur Utama

- 📍 **GPS Real-Time Tracking**  
  Melacak lokasi tongkat secara langsung dari aplikasi.
  
- 🚨 **Tombol Darurat (SOS)**  
  Ketika ditekan, akan mengirim sinyal darurat dan mengaktifkan LED otomatis.

- 💡 **Lampu LED Sorot 10W**  
  Dapat dinyalakan dari aplikasi saat berada di tempat gelap.

- 📱 **Kontrol dari Jarak Jauh**  
  Komunikasi via SIM800L memungkinkan fitur dikendalikan dari HP.

- 🔋 **Baterai 5000mAh + Charging Module**  
  Daya tahan lama dengan modul pengisian TP4056.

---

## 🧠 Teknologi & Komponen

| Komponen       | Spesifikasi                  | Fungsi                     |
|----------------|------------------------------|----------------------------|
| Mikrokontroler | ESP32 DEVKITC V4             | Kontrol utama              |
| GPS            | NEO-7M                       | Pelacakan lokasi           |
| GSM Module     | SIM800L                      | Komunikasi (MQTT)          |
| LED            | HPL 10W Putih                | Penerangan jarak jauh      |
| Tombol         | Push Button 12mm             | Trigger SOS                |
| Baterai        | 5000mAh + TP4056             | Sumber & pengisi daya      |
| Struktur       | Pipa PVC                     | Rangka tongkat             |

---

## 🚀 Cara Menggunakan

Clone repositori ini:
```bash
git clone https://github.com/namakamu/smart-walking-cane.git
