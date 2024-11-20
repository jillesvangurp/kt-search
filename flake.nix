{
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs/nixos-unstable";
    flake-utils.url = "github:numtide/flake-utils";
  };

  outputs =
    {
      self,
      nixpkgs,
      flake-utils,
    }:
    flake-utils.lib.eachDefaultSystem (
      system:
      with nixpkgs.legacyPackages.${system};
      let
        libraries = with pkgs; [
          curl
        ];
      in
      {
        formatter = nixpkgs-fmt;

        devShell = mkShell {
          packages = [
            jq
            git
          ] ++ libraries;

          env = {
            LDFLAGS = "${pkgs.lib.makeLibraryPath libraries}";
          };
        };
      }
    );
}
