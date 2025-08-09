#! /usr/bin/env bash
cd docs/build/manual-quarto || (echo "Quarto does not exist" && exit 1)
docker run --rm --platform=linux/amd64 -v "$PWD":/work -w /work ghcr.io/quarto-dev/quarto:1.6.40 quarto render . --to html
#docker run --rm --platform=linux/amd64 -v "$PWD":/work -w /work ghcr.io/quarto-dev/quarto:1.6.40 quarto render . --to pdf
docker run --rm --platform=linux/amd64 -v "$PWD":/work -w /work ghcr.io/quarto-dev/quarto:1.6.40 quarto render . --to epub