# Biblioteca visual — Cubo Mestre 3D

Biblioteca de trabalho, fora de `app/src/main/res`: estes arquivos não são incluídos automaticamente no APK. Adicionada em 30/10/2026, a partir do projeto no commit 4b4bde4.

## Comece aqui

Abra `catalogo.html` no navegador. Extraia os ZIPs nas próprias pastas para navegar nos pacotes completos. No Windows: botão direito → Extrair tudo.

| Pasta | Conteúdo | Licença / origem |
|---|---|---|
| icons/material-symbols-rounded/svg | 32 ícones selecionados, originais SVG | Apache 2.0 — Google |
| icons/material-symbols-rounded/android | Os mesmos 32 ícones convertidos para VectorDrawable | Apache 2.0 — derivados dos SVG Google |
| icons/lucide | Biblioteca completa de 2.015 SVGs em ZIP, com licença | ISC e avisos adicionais no LICENSE |
| fonts | Space Grotesk, Plus Jakarta Sans e JetBrains Mono, copiadas do app; OFL de cada família | SIL OFL 1.1 |
| ui-packs/kenney | UI Pack completo em ZIP, prévias PNG e licença | CC0 |
| identity | Paleta extraída de Color.kt | Identidade atual do projeto |
| components | Orientações para usar os componentes já existentes em Compose | Documentação do projeto |

## Identidade confirmada no código

Android nativo em Kotlin, Jetpack Compose / Material 3 e renderizador próprio OpenGL ES 2.0 (`CubeGLRenderer.kt`). A árvore atual não contém projeto Unreal Engine. Fundo marinho/grafite, turquesa e violeta, cores vivas do cubo, títulos Space Grotesk, corpo Plus Jakarta Sans e notação JetBrains Mono. Manter também contraste no tema claro.

## Integração

- Usar Material como família principal; Lucide é alternativa. Não misturar estilos indiscriminadamente.
- Copiar somente os VectorDrawables escolhidos para `app/src/main/res/drawable`. Usar `painterResource` e tint conforme o tema. Ícones direcionais precisam de espelhamento RTL na integração.
- Fontes já estão no app; não é necessário duplicá-las em runtime. As cópias aqui servem a ferramentas de design.
- Kenney é uma referência opcional de interface de jogo. Recolorir e selecionar elementos coerentes com a paleta; preservar botões funcionais em Compose.
- Diagramas que ensinam algoritmos devem vir do estado real do cubo/renderizador: assets genéricos não substituem uma representação matematicamente correta.
- Capas exclusivas e animações Lottie ainda não foram criadas ou incluídas. Nenhuma dependência nova foi instalada e nenhuma tela foi alterada.

## Fontes dos downloads

- Material: https://github.com/google/material-design-icons (32 SVGs rounded, 24px; branch master, consulta 30/10/2026).
- Lucide: https://github.com/lucide-icons/lucide — commit a630cec77eac6568b989e87e4947a8bfe45caacd. ZIP organizado somente com SVGs e LICENSE, sem dependências de desenvolvimento.
- Kenney: https://kenney.nl/assets/ui-pack — cópia CC0 obtida de https://opengameart.org/sites/default/files/kenney_ui-pack.zip após o download direto retornar 403. Pacote do espelho pode diferir da versão mais recente.
- Fontes: binários existentes em `app/src/main/res/font`, licenças das famílias no repositório https://github.com/google/fonts.

Os SHA-256 dos arquivos estão em `manifest.json`. Guardar as licenças ao redistribuir assets.
