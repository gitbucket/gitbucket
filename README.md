GitBucket [![Gitter chat](https://badges.gitter.im/gitbucket/gitbucket.svg)](https://gitter.im/gitbucket/gitbucket) [![build](https://github.com/gitbucket/gitbucket/actions/workflows/build.yml/badge.svg)](https://github.com/gitbucket/gitbucket/actions/workflows/build.yml) [![gitbucket Scala version support](https://index.scala-lang.org/gitbucket/gitbucket/gitbucket/latest-by-scala-version.svg)](https://index.scala-lang.org/gitbucket/gitbucket/gitbucket) [![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)](https://github.com/gitbucket/gitbucket/blob/master/LICENSE)
=========

## Sekilas Tentang

GitBucket adalah sebuah platform web untuk Git yang dibangun dengan Scala, dirancang untuk memberikan pengalaman mirip GitHub dengan kemudahan instalasi dan fleksibilitas tinggi.

**✨ Fitur Utama**

- 🚀 Instalasi mudah dan siap digunakan
- 🎨 Antarmuka intuitif dan ramah pengguna
- 🔌 Sistem plug-in untuk memperluas fungsionalitas
- 🔄 Kompatibel dengan API GitHub
- 🔒 Mendukung repository publik maupun privat (akses via http/https dan ssh)
- 📂 GitLFS support untuk file besar
- 👀 Repository viewer lengkap dengan online file editor
- 📌 Manajemen proyek dengan Issues, Pull Requests, dan Wiki
- 🕑 Activity timeline & email notifications
- 👥 Manajemen akun dan grup, termasuk integrasi LDAP


![GitBucket](https://gitbucket.github.io/img/screenshots/screenshot-repository_viewer.png)

## Instalasi

_**1. Persiapan Server**_

Pastikan sudah ada:

* **Java 11+**
* Akses root / sudo
* VPS dengan IP publik

Update sistem:

``` 
sudo apt update && sudo apt upgrade -y
```

Install Java:

``` 
sudo apt install openjdk-11-jre -y
```


_**2. Buat User Khusus untuk GitBucket**_

``` 
sudo adduser --system --group --home /opt/gitbucket gitbucket
```


_**3. Download GitBucket**_

``` 
cd /opt/gitbucket
sudo wget https://github.com/gitbucket/gitbucket/releases/download/4.39.0/gitbucket.war
sudo chown gitbucket:gitbucket gitbucket.war
```


_**4. Buat Systemd Service**_

Buat file service:

``` 
sudo nano /etc/systemd/system/gitbucket.service
```

Isi dengan:

```
[Unit]
Description=GitBucket Service
After=network.target

[Service]
WorkingDirectory=/opt/gitbucket
ExecStart=/usr/bin/java -Xms128m -Xmx256m -jar gitbucket.war
User=gitbucket
Group=gitbucket
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

Reload & enable service:

```
sudo systemctl daemon-reexec
sudo systemctl enable gitbucket
sudo systemctl start gitbucket
```

Cek status:

``` 
sudo systemctl status gitbucket
```


_**5. Akses GitBucket**_

Secara default, GitBucket berjalan di port `8080`.
Coba akses di browser:

```
http://<IP-VPS>:8080
```

Jika memakai domain, buat subdomain (misal `gitbucket.example.com`) → arahkan ke IP VPS via DNS `A Record`.


_**6. Setup Reverse Proxy dengan Nginx**_

Install Nginx:

``` 
sudo apt install nginx -y
```

Buat konfigurasi:

``` 
sudo nano /etc/nginx/sites-available/gitbucket.conf
```

Isi:

```
server {
    listen 80;
    server_name gitbucket.example.com;

    location / {
        proxy_pass http://127.0.0.1:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
    }
}
```

Aktifkan config:

``` 
sudo ln -s /etc/nginx/sites-available/gitbucket.conf /etc/nginx/sites-enabled/
sudo nginx -t
sudo systemctl reload nginx
```

Coba akses:

```
http://gitbucket.example.com
```


_**7. Tambahkan HTTPS (Opsional tapi Disarankan)**_

Gunakan **Certbot** untuk SSL:

``` 
sudo apt install certbot python3-certbot-nginx -y
sudo certbot --nginx -d gitbucket.example.com
```

Pilih opsi redirect → selesai.
Sekarang akses dengan:

```
https://gitbucket.example.com
```


## Cara Pemakaian
```
  1. Dashboard.
<img width="735" height="373" alt="image" src="https://github.com/user-attachments/assets/05bc5a38-f118-4403-a446-5092bb4a84b9" />

Penjelasan = Dashboard berfungsi sebagai halaman beranda utama setelah pengguna masuk ke platform. Fitur ini menyajikan tinjauan umum mengenai aktivitas terkini, menyertakan News Feed yang menampilkan pembaruan dari repositori yang diikuti. Pengguna memanfaatkannya sebagai pusat navigasi untuk memantau aktivitas keseluruhan akun dan mengakses cepat repositori yang baru saja diperbarui. Anda bisa menelusuri fitur lainnya ketika berada disini sebab seluruh fitur telah terpresentasi dan terlihat dalam ui dashboard.

  2. File.
<img width="735" height="373" alt="image" src="https://github.com/user-attachments/assets/05bc5a38-f118-4403-a446-5092bb4a84b9" />

Penjelasan = Files adalah tampilan utama di dalam repositori yang menunjukkan hierarki folder dan file dari kode sumber proyek pada branch tertentu. Fungsi utamanya adalah menyajikan status kode terbaru dan commit terakhir yang dianggap relevan. Pengguna menggunakannya untuk menelusuri isi proyek secara detail, memeriksa struktur kode, mengamati history perubahan file, serta melihat daftar file. Ini bisa di akses pada menu file.

  3. Branches.
<img width="730" height="380" alt="image" src="https://github.com/user-attachments/assets/f6815385-eb1a-4689-b02f-abe49d17b2e3" />

Penjelasan =  Fitur Branches mengelola semua jalur pengembangan independen dalam proyek. Fungsinya memastikan stabilitas kode utama dengan mengisolasi atau menjaga pekerjaan fitur atau perbaikan bug yang sedang dilakukan atau sedang berlangsung. Pengguna membuat branch baru untuk melakukan pekerjaan tanpa mengganggu kode utama, serta membandingkan dan mengelola perkembangan task/pekerjaan.

  4. Releases.
  <img width="749" height="353" alt="image" src="https://github.com/user-attachments/assets/30107764-bc0f-46d5-9860-2bb570d72b86" />

Penjelasan = Releases digunakan untuk menandai secara resmi versi stabil dan teruji dari perangkat lunak, biasanya dihubungkan dengan tag Git tertentu. Fungsinya adalah menyediakan titik sejarah yang terdefinisi dengan baik dan siap didistribusikan kepada pengguna akhir atau sistem lainnya. Pengguna memanfaatkan fitur ini untuk melacak sejarah versi produk dan menyediakan paket unduhan sumber kode yang relevan.

  5. Issues.
  <img width="747" height="286" alt="image" src="https://github.com/user-attachments/assets/b92c07d7-6b62-4cdd-af46-86e66f62086e" />

Penjelasan = Issues Merupakan sistem terstruktur untuk mencatat, mengelola, dan mendiskusikan bug, permintaan fitur, serta tugas yang perlu diselesaikan. Fungsi utamanya adalah terletak pada menyediakan pusat terpadu untuk mengatur dan lalu memprioritaskan semua pekerjaan yang diperlukan proyek. Pengguna menggunakannya untuk melaporkan masalah baru, memberikan label, menetapkan penanggung jawab, dan melacak kemajuan solusi.

  6. Pull Request.
  <img width="747" height="286" alt="image" src="https://github.com/user-attachments/assets/80f8d781-c621-4d77-9c85-1937e4736650" />
  
PPull Requests (PR) pada GitBucket merupakan mekanisme penting yang membuat pengguna bisa mengajukan perubahan kode yang telah mereka kembangkan pada branch terpisah agar dapat diintegrasikan ke branch utama proyek, seperti master. Fungsi utamanya adalah memfasilitasi proses tinjauan kode / code review secara kolaboratif, dimana anggota tim dapat memeriksa, mendiskusikan, dan memberikan umpan balik pada diff / perbedaan kode sebelum persetujuan diberikan. Setelah mendapatkan persetujuan nantinya, PR memungkinkan pengguna atau pengelola repositori untuk secara resmi menggabungkan (merge) perubahan tersebut, memastikan bahwa setiap kontribusinya telah melewati verifikasi sebelum menjadi bagian permanen dari basis kode proyek.
 ```

## Pembahasan

- Pendapat anda tentang aplikasi web ini
    - kelebihan
    - kekurangan
- Bandingkan dengan aplikasi web lain yang sejenis


## Referensi

Cantumkan tiap sumber informasi yang anda pakai.

