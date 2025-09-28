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

- Tampilan aplikasi web
- Fungsi-fungsi utama
- Isi dengan data real/dummy (jangan kosongan) dan sertakan beberapa screenshot


## Pembahasan

- Pendapat anda tentang aplikasi web ini
    - kelebihan
    - kekurangan
- Bandingkan dengan aplikasi web lain yang sejenis


## Referensi

Cantumkan tiap sumber informasi yang anda pakai.

