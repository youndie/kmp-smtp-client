#!/usr/bin/env sh
# Certificates for the TLS tests: a private CA, and a server certificate it signs for localhost.
#
# Not committed to the repository on purpose — a certificate in git expires and then fails tests
# for a reason that has nothing to do with the code.
set -eu

out="${1:-build/e2e-certs}"
mkdir -p "$out"

if [ -f "$out/ca.pem" ] && [ -f "$out/server.pem" ]; then
    echo "certificates already present in $out"
    exit 0
fi

openssl req -x509 -newkey rsa:2048 -nodes -days 365 \
    -subj "/CN=kmp-smtp-client test CA" \
    -keyout "$out/ca-key.pem" -out "$out/ca.pem" 2>/dev/null

openssl req -newkey rsa:2048 -nodes \
    -subj "/CN=localhost" \
    -keyout "$out/server-key.pem" -out "$out/server.csr" 2>/dev/null

# The name is checked against the SAN, not the CN: rfc9525 retired the CN fallback.
printf 'subjectAltName=DNS:localhost,IP:127.0.0.1\n' > "$out/server.ext"

openssl x509 -req -in "$out/server.csr" -days 365 \
    -CA "$out/ca.pem" -CAkey "$out/ca-key.pem" -CAcreateserial \
    -extfile "$out/server.ext" \
    -out "$out/server.pem" 2>/dev/null

chmod 644 "$out"/*.pem
echo "certificates written to $out"
