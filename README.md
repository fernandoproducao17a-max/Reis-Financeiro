# Reis Financeiro

Aplicativo Android de controle financeiro pessoal, com lançamento manual e por voz em português do Brasil.

## Já implementado

- Dashboard com saldo atual
- Valor inicial configurável e editável
- Entradas e saídas
- Banco local com Room
- Histórico de lançamentos
- Edição e exclusão
- Filtros por tipo e categorias principais
- Relatório resumido
- Gastos agrupados por categoria
- Categorias financeiras comuns
- Comandos por voz em pt-BR
- Valores como 1.250,50 e “3 mil reais”
- Identidade visual e ícone Reis
- GitHub Actions para gerar APK debug

## Exemplos de voz

- “Gastei 100 reais de combustível”
- “Paguei 1.250,50 de energia”
- “Recebi 3 mil reais de salário”
- “Comprei 80 reais no mercado”

## Arquitetura

Kotlin + Jetpack Compose + Room + Android SpeechRecognizer.

O banco é local para que os lançamentos continuem disponíveis sem depender de internet.

## Atalho e Assistente

O app também registra um atalho de “Novo lançamento” para o launcher/assistente compatível. Isso permite iniciar rapidamente o fluxo de voz. A disponibilidade de invocação por voz depende do Android/assistente e da configuração do aparelho.

## Ativação por “Reis”

A palavra-chave fora do aplicativo depende dos mecanismos oficiais do Android/assistente e das permissões disponíveis no aparelho. O app não mantém o microfone permanentemente ativo por conta própria.

## Build

O workflow `.github/workflows/android.yml` gera um APK debug em pushes na branch `main` e disponibiliza o APK como artefato do GitHub Actions.
