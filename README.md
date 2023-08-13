# Datapack Linter
[![stars](https://img.shields.io/github/stars/ChenCMD/datapack-linter?logo=github)](https://github.com/ChenCMD/datapack-linter/stargazers)
[![activity](https://img.shields.io/github/commit-activity/m/ChenCMD/datapack-linter?label=commit&logo=github)](https://github.com/ChenCMD/datapack-linter/commits/main)
[![workflow](https://img.shields.io/github/actions/workflow/status/ChenCMD/datapack-linter/lint-datapack-dev.yml?branch=main&label=linter)](https://github.com/ChenCMD/datapack-linter/actions?query=workflow%3Atest-run-and-deploy)
[![Gitmoji](https://img.shields.io/badge/gitmoji-%20😜%20😍-FFDD67.svg)](https://gitmoji.carloscuesta.me/)

この GitHub Action は変更が行われた datapack ドキュメントの静的解析を行います。

This GitHub Action performs a static analysis of the datapack document in which the changes were made.


## 使い方 / Usage
1. GitHub の自身のリポジトリで Actions タブへ行き `New workflow` をクリック

   Go to your own repository on GitHub, go to the Actions tab and click on `New workflow`.

1. 下の方にある `Manual workflow` を選択し下記の設定ファイルをコピー & ペースト

   Select the Manual workflow at the bottom and copy and paste the following configuration file

2. 右上の `Start commit` -> `Commit new file` を押す

   Press `Start commit` -> `Commit new file` in the upper right corner.
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
       steps:
         - name: checkout repository
           uses: actions/checkout@v3
           with:
             fetch-depth: 0

         - name: lint
           uses: ChenCMD/datapack-linter@v2
   ```


## 校閲ルールについて / Lint Rules
Datapack Linter は [Datapack Helper Plus](https://github.com/SPGoding/vscode-datapack-helper-plus) に使用されている言語サーバーを使用しており、リポジトリルートに配置された `.vscode/setting.json` を自動的に読み込み校閲ルールに適用します。  
このファイルは VSCode でワークスペースの設定を変更すると生成されます。  
また、後述の[入力](#入力--inputs)から `configPath` を指定することで、任意のコンフィグファイルを読み込むことが可能です。

The Datapack Linter uses the same language server used by [Datapack Helper Plus](https://github.com/SPGoding/vscode-datapack-helper-plus), and automatically reads `.vscode/setting.json` placed in the repository root and applies it to the review rules.  
This file is generated when you change the workspace settings in VSCode.  
It is also possible to load an arbitrary config file by specifying `configPath` from the [inputs](#入力--inputs) described below.


## 入力 / Inputs
|      名前  Name       | 想定する値 / Expect value | 必須 / Require |   デフォルト / Default    | 概要 / About                                                                                                                                                                             |
| :-------------------: | :-----------------------: | :------------: | :-----------------------: | :--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
|     lintDirectory     |           path            |       x        |           `"."`           | チェックを行うディレクトリ<br>Directory to lint                                                                                                                                          |
|      configPath       |           path            |       x        | `".vscode/settings.json"` | 校閲ルールを記載したコンフィグファイルのパス<br>Path to the config file containing the lint rules                                                                                        |
|       forcePass       |          `true`           |       x        |          `false`          | チェックに失敗した Datapack ドキュメントが存在するときに step そのものを失敗させるか否か<br>Whether or not to fail the step itself when there is a Datapack document that fails the lint |
|   muteSuccessResult   |          `true`           |       x        |          `false`          | チェックに成功した Datapack ドキュメントのログを無効化するか否か<br>Whether to disable logging of Datapack documents that have been successfully linted                                  |
| ignoreLintPathPattern |        globPattern        |       x        |           `""`            | チェックを行わない Datapack ドキュメントのリソースパスの globPattern<br>globPatterns of the resource path of the Datapack document whose lints are to be ignored                          |
|  alwaysCheckAllFile   |          `true`           |       x        |          `false`          | 常にすべての Datapack ドキュメントをチェックするか否か<br>Whether to always lint all Datapack documents                                                                                  |

注: `ignoreLintPathPattern` は複数の文字列を入れることが可能です。方法は下記のサンプルを参照してください。

note: `ignoreLintPathPattern` can contain multiple character strings. Please refer to the sample below for the method.
```yaml
         - name: lint
           uses: ChenCMD/datapack-linter@v2
           with:
             ignoreLintPathPattern: |
               ignore:**
               example:ignore/**
               example:data/ignore/**
```


## キャッシュについて / Cache
Datapack Linter はキャッシュを利用して、動作速度を高速にしています。  
しかし、さまざまな要因で稀にキャッシュが破損する可能性があります。  
その場合、コミットメッセージに `[regenerate cache]` という文字列を含めることでキャッシュを再生成することが可能です。

The Datapack Linter uses a cache to speed up its operation.  
However, there is a rare possibility that the cache gets corrupted due to various reasons.  
In that case, you can regenerate the cache by including the string `[regenerate cache]` in the commit message.


## コントリビュートについて / Contribution
[CONTRIBUTING.md](CONTRIBUTING.md) を確認してください

Please check [CONTRIBUTING.md](CONTRIBUTING.md).
