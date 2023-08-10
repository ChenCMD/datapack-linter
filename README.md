# Datapack Linter
このGitHubActionはdatapackの変更があったファイルの構文チェックを行います。

This GitHubAction will perform a syntax check of the files that have been modified in datapack.

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
         - name: checkout repository
           uses: actions/checkout@v2
           with:
             fetch-depth: 0

         - name: lint
           uses: ChenCMD/datapack-linter@v1
   ```

## 校閲ルールについて / Lint Rules
datapack linterは[Datapack Helper Plus](https://github.com/SPGoding/vscode-datapack-helper-plus)に使用されている言語サーバーを使用しており、
リポジトリルートに配置された`.vscode/setting.json`を自動的に読み込み校閲ルールに適用します。

このファイルはVSCodeでワークスペースの設定を変更すると生成されます。

The datapack linter uses the same language server used by [Datapack Helper Plus](https://github.com/SPGoding/vscode-datapack-helper-plus),
and automatically reads `.vscode/setting.json` placed in the repository root and applies it to the review rules.

This file is generated when you change the workspace settings in VSCode.

## 入力 / Inputs
|      名前  Name       |   想定する値 / Expect value   | 必須 / Require | 概要 / About                                                                                                                                                        |
| :-------------------: | :---------------------------: | :------------: | :------------------------------------------------------------------------------------------------------------------------------------------------------------------ |
|     outputDefine      | リソースパス</br>Resourcepath |       x        | 入力されたリソースパスに一致するdefine/declareを表示します<br>Displays the define/declare that match the input resource path.                                       |
|       forcePass       |            `true`             |       x        | `true`に設定した場合、チェックに失敗したファイルが存在してもfailしません。<br>If set to `true`, it will not fail even if there are files that failed to be checked. |
|   notOutputSuccess    |            `true`             |       x        | `true`に設定した場合、チェックに成功したファイルのログを出力しません。<br>If set to `true`, do not output the log of files that have been successfully checked.     |
| ignoreLintPathPattern |          globPattern          |       x        | 入力されたglobPatternに一致したファイルをチェックしません。<br>Do not check for files that match the entered globPattern.                                           |
|  checkAlwaysAllFile   |            `true`             |       x        | `true`に設定した場合、キャッシュを使用せず常にすべてのファイルをチェックします。<br>If set to `true`, it will always check all files without using the cache.       |

注: outputDefine及びignoreLintPathPatternは複数の文字列を入れることが可能です。方法は下記のサンプルを参照してください。
note: outputDefine and ignoreLintPathPattern can contain multiple character strings. Please refer to the sample below for the method.
```yaml
         - name: lint
           uses: ChenCMD/datapack-linter@v1
           with:
             ignoreLintPathPattern: |
               ignore:**
               example:ignore/**
               example:data/ignore/**
```


## キャッシュについて / Cache
datapack linterはキャッシュを利用して、動作速度を高速にしています。  
しかし、さまざまな要因で稀にキャッシュが破損する可能性があります。その場合コミットメッセージに`[regenerate cache]`という文字列を含めることでキャッシュを再生成することが可能です。

The datapack linter uses a cache to speed up its operation.  
However, there is a rare possibility that the cache gets corrupted due to various reasons. In that case, you can regenerate the cache by including the string `[regenerate cache]` in the commit message.