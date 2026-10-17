#!/usr/bin/env sh
# Gera as imagens da Play Store a partir dos SVGs desta pasta.
# Precisa do rsvg-convert (pacote librsvg). Uso: sh docs/loja/gerar-imagens.sh
set -e
cd "$(dirname "$0")"

# Ícone: 512x512, PNG 32 bits.
rsvg-convert -w 512 -h 512 icone.svg -o icone-512.png

# Imagem de destaque: 1024x500. A Play Store não aceita transparência aqui;
# o SVG já tem fundo opaco.
rsvg-convert -w 1024 -h 500 banner.svg -o banner-1024x500.png

echo "Geradas: icone-512.png e banner-1024x500.png"
