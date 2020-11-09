# Datapack Linter

このソースコードを自分のdatapack開発リポジトリに含めるとdatapackの構文チェックをpush/pull_requestの度に行います。

If you include this source code in your datapack development repository, it will check the syntax of datapack every push / pull_request.

## 使い方 / How to use

1. releasesよりdatapack-linter.zipを入手し自身のdatapackリポジトリに中身を解凍

   Get datapack-linter.zip from releases and extract the contents into your datapack repository.
1. GitHubの自身のリポジトリでActionsタブへ行き`New workflow`をクリック

   Go to your own repository on GitHub, go to the Actions tab and click on `New workflow`.
1. 下の方にある`Manual workflow`を選択し[.github/workflows/lint-datapack.yml](https://github.com/ChenCMD/datapack-linter/blob/main/.github/workflows/lint-datapack.yml)の中身をコピー&ペースト

   select `Manual workflow` at the bottom and copy and paste the contents of [.github/workflows/lint-datapack.yml](https://github.com/ChenCMD/datapack-linter/blob/main/.github/workflows/lint-datapack.yml)
1. 右上の`Start commit` -> `Commit new file`を押し準備完了

   Press `Start commit` -> `Commit new file` in the upper right corner to get ready.