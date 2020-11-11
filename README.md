# Datapack Linter

このGitHubActionはdatapackの構文チェックを行います。

This GitHubAction performs a datapack syntax check.

## 使い方 / Usage

1. GitHubの自身のリポジトリでActionsタブへ行き`New workflow`をクリック
   
   Go to your own repository on GitHub, go to the Actions tab and click on `New workflow`.

1. 下の方にあるManual workflowを選択し下記の設定ファイルをコピー&ペースト

   Select the Manual workflow at the bottom and copy and paste the following configuration file

1. 右上の`Start commit` -> `Commit new file`を押し準備完了

   Press `Start commit` -> `Commit new file` in the upper right corner to get ready.
```yaml
name: lint-datapack
on:
  push:
  pull_request:
  workflow_dispatch:
jobs:
  lint:
    name: lint
    runs-on: ubuntu-latest
    if: "!contains(github.event.head_commit.message, '[skip ci]')"
    steps:
      - uses: actions/checkout@v2
        with:
          fetch-depth: 0
      - name: Test
        uses: ChenCMD/datapack-linter@main
```

## 校閲ルールについて / Lint Rules

datapack linterは[Datapack Helper Plus](https://github.com/SPGoding/vscode-datapack-helper-plus)の実装を使用しており、
リポジトリルートに配置された.vscode/setting.jsonを自動的に読み込み校閲ルールに適用します。

このファイルはVSCodeでワークスペースの設定を変更すると生成されます。

The datapack linter uses the [Datapack Helper Plus](https://github.com/SPGoding/vscode-datapack-helper-plus) implementation, and the
Automatically read the .vscode/setting.json file placed in the repository root and apply it to the review rules.

This file is generated when you change the workspace settings in VSCode.
