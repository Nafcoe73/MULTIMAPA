# Mapa Offline — app Android em Java puro

Sem Python, sem Kivy, sem bibliotecas: só o SDK do Android. APK com ~100-200 KB.
Funciona com os mesmos ficheiros da app do PC (`mapa.png`, `rota.txt`, `ruas.bin`).

## Compilar no GitHub (sem instalar nada)

1. Cria um repositório **novo** no GitHub (pode ser privado).
2. **Add file → Upload files** e arrasta **o conteúdo** desta pasta (incluindo a pasta `app`
   e a pasta `.github`). Se o navegador não mostrar a pasta `.github` (está "escondida"),
   cria o ficheiro à mão: **Add file → Create new file**, nome `.github/workflows/build.yml`,
   e cola o conteúdo de `build.yml`.
3. Separador **Actions** → "Compilar APK" (~3-5 min).
4. No fim, em **Artifacts**, descarrega `mapa-offline-apk` → dentro está `MapaOffline.apk`.
5. Instala no telemóvel (aceita "instalar apps desconhecidas").
   Se tinhas a versão Kivy instalada, desinstala-a primeiro (mesmo nome, assinatura diferente).

As versões seguintes instalam-se por cima sem perder os mapas (a chave de assinatura
`app/debug.keystore` vai incluída no projeto).

## Pôr os mapas no telemóvel (podes ter vários)

Cada mapa vive na sua pasta. A app do PC já grava cada rota numa subpasta com o nome
do destino (ex.: `rota_offline/Praça do Comércio, Lisboa/`).

**A) Importar (mais fácil):** copia para o telemóvel a pasta de um mapa, ou a pasta
`rota_offline` inteira com várias subpastas (ex.: para `Download/rota_offline`).
Na app: **Mapas → Importar…** e escolhe essa pasta. Se tiver subpastas, importa todas.
(O Android não deixa escolher a pasta Download em si: usa uma subpasta.)

**B) USB direto:** cada mapa numa subpasta de
`Armazenamento interno/Android/data/org.rota.mapaoffline/files/`
(ex.: `files/Lisboa/mapa.png`, `files/Lisboa/rota.txt`, `files/Lisboa/ruas.bin`).

**Escolher / apagar:** botão **Mapas** → toca no mapa que queres (o atual tem ✔).
**Apagar…** remove um mapa do telemóvel. A app lembra-se do último mapa usado.

## Ecrã

- Cima: destino, distância e direção em linha reta, precisão do GPS, satélites.
- Mapa: Norte para cima (N/S/E/O nas margens), destino **verde**, tu **vermelho** (com círculo
  de precisão e seta do movimento), zoom com dois dedos ou toque duplo.
- Baixo (navegação): **azul** normal · **laranja** a < 100 m da manobra · **vermelho** fora da rota.
- Botões: Onde estou · Destino · Seguir · Mapa todo · Mapas (escolher, importar, apagar).
- O ecrã fica sempre ligado enquanto a app está aberta.
