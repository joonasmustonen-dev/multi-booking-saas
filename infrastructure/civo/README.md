# First Civo staging deployment

This bundle runs the backend, Keycloak, PostgreSQL and Caddy on one Civo
Compute VM. The database network is private and only Caddy publishes ports.
Use it to validate deployment and restore procedures before accepting customer
data. The `staging` Spring profile permits unencrypted PostgreSQL traffic only
inside the private Docker network. The stricter `prod` profile remains intended
for a managed PostgreSQL connection with verified TLS.

## 1. DNS and Civo

Create two DNS `A` records pointing to the VM public IPv4 address:

```text
api.example.com   -> VM_IP
auth.example.com  -> VM_IP
```

The Civo firewall should allow inbound TCP 22, 80 and 443, and UDP 443. Do not
open ports 5432, 8080 or 8081. Attach a volume (25 GB is sufficient for
staging), format it, and mount it at `/mnt/booking-data`. Civo's volume setup
instructions must be followed before continuing.

## 2. Install and clone

Run on the VM:

```bash
sudo apt update
sudo apt upgrade -y
sudo apt install -y docker.io docker-compose-v2 git curl ufw unattended-upgrades
sudo systemctl enable --now docker
sudo usermod -aG docker "$USER"
exit
```

Reconnect, then clone the repository:

```bash
ssh civo@VM_IP
git clone YOUR_GITHUB_REPOSITORY_URL booking
cd booking/infrastructure/civo
```

For a private repository, use a GitHub deploy key or clone locally and transfer
an archive. Do not put a personal access token in a command or shell history.

## 3. Configure secrets

```bash
cp .env.example .env
chmod 600 .env
```

Edit `.env` and replace the hosts. Generate every password independently:

```bash
openssl rand -hex 32
```

Never commit `.env`. Prepare the attached volume directory:

```bash
sudo mkdir -p /mnt/booking-data/postgres
sudo chown -R 999:999 /mnt/booking-data/postgres
```

## 4. Validate and start

```bash
docker compose config --quiet
docker compose build backend
docker compose up -d
docker compose ps
docker compose logs --tail=100
```

Caddy requests public certificates, so DNS must already resolve to this VM.
Check both endpoints:

```bash
curl -fsS https://api.example.com/api/health
curl -fsS https://auth.example.com/realms/booking/.well-known/openid-configuration >/dev/null
```

The first backend start creates the platform schema. Provision the initial
tenant and restart the backend so its tenant migrations run:

```bash
chmod +x provision-demo-tenant.sh backup.sh
./provision-demo-tenant.sh
docker compose logs --tail=100 backend
```

## 5. Create the first login

Open `https://auth.example.com/admin/`, sign in with the bootstrap administrator
from `.env`, select the `booking` realm, and create a user. Set a permanent
password and assign the `TENANT_ADMIN` realm role. The imported frontend client
already uses PKCE, the `booking-backend` audience, tenant `demo`, and the
Netlify redirect origin.

Configure SMTP under **Realm settings -> Email** before relying on password
reset. After creating a separate permanent platform administrator, remove or
rotate the bootstrap administrator credentials.

## 6. Connect Netlify

Set these Netlify build environment variables:

```text
VITE_API_URL=https://api.example.com
VITE_KEYCLOAK_URL=https://auth.example.com
VITE_KEYCLOAK_REALM=booking
VITE_KEYCLOAK_CLIENT_ID=booking-frontend
```

Use build command `npm run build`, base directory `frontend`, and publish
directory `dist`. Clear the build cache and deploy again. The backend's
`FRONTEND_ORIGIN` in `.env` must exactly match the Netlify origin.

## 7. Back up and update

Create a local logical backup:

```bash
./backup.sh
```

Copy every backup to encrypted storage outside this VM. A Civo volume is not a
backup. Test restoring into a disposable staging server before launch.

For an application update:

```bash
cd ~/booking
git pull --ff-only
cd infrastructure/civo
docker compose build backend
docker compose up -d
docker compose logs --tail=100 backend
```

Do not run `docker compose down -v`; `-v` deletes named Caddy volumes. Do not
delete `/mnt/booking-data/postgres` unless a verified backup has been restored
elsewhere.
