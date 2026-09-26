#!/usr/bin/env bash
# One-time setup of the Oracle Cloud server (Ubuntu 24.04). Safe to run again.
# Works on the 1 GB "E2.1.Micro" shape: adds swap so Java, Postgres and Redis fit.
#
# Run from the project folder on Windows (PowerShell):
#   Get-Content deploy\server-setup.sh -Raw | ssh -i $HOME\Downloads\ssh-key-2026-09-26.key ubuntu@129.154.226.175 "tr -d '\r' | bash -s"
set -euo pipefail

echo "==> 1/4 Swap (4 GB)"
if ! sudo swapon --show | grep -q '/swapfile'; then
  sudo fallocate -l 4G /swapfile
  sudo chmod 600 /swapfile
  sudo mkswap /swapfile >/dev/null
  sudo swapon /swapfile
  grep -q '^/swapfile ' /etc/fstab || echo '/swapfile none swap sw 0 0' | sudo tee -a /etc/fstab >/dev/null
fi
echo 'vm.swappiness=20' | sudo tee /etc/sysctl.d/99-examprep.conf >/dev/null
sudo sysctl -q -p /etc/sysctl.d/99-examprep.conf

echo "==> 2/4 Docker"
if ! command -v docker >/dev/null; then
  sudo apt-get update -qq
  sudo DEBIAN_FRONTEND=noninteractive apt-get install -y -qq docker.io docker-compose-v2 git >/dev/null
fi
# Keep container logs small on a small disk.
sudo mkdir -p /etc/docker
echo '{"log-driver":"json-file","log-opts":{"max-size":"10m","max-file":"3"}}' | sudo tee /etc/docker/daemon.json >/dev/null
sudo systemctl enable --now docker >/dev/null
sudo systemctl restart docker
sudo usermod -aG docker "$USER"

echo "==> 3/4 Firewall: open ports 80 and 443 (Oracle images block them by default)"
for port in 80 443; do
  if ! sudo iptables -C INPUT -p tcp -m state --state NEW --dport "$port" -j ACCEPT 2>/dev/null; then
    reject=$(sudo iptables -L INPUT --line-numbers -n | awk '$2=="REJECT"{print $1; exit}')
    sudo iptables -I INPUT "${reject:-1}" -p tcp -m state --state NEW --dport "$port" -j ACCEPT
  fi
done
sudo netfilter-persistent save >/dev/null 2>&1 || true

echo "==> 4/4 App folder"
mkdir -p ~/examprep

echo
echo "Done. Summary:"
free -h | awk 'NR<=3'
docker --version
docker compose version
sudo iptables -L INPUT -n | grep -E 'dpt:(80|443)' || true
echo "Log out and back in once so 'docker' works without sudo."
