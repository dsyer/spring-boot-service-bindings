with import <nixpkgs> { };
let
  imgpkg = stdenv.mkDerivation {
    pname = "imgpkg";
    version = "0.36.1";
    src = fetchurl {
      # nix-prefetch-url this URL to find the hash value
      url =
        "https://github.com/vmware-tanzu/carvel-imgpkg/releases/download/v0.36.1/imgpkg-linux-amd64";
      sha256 = "1k0mgkdcw4lg7ciiy9ys508vf36day92rd152a68jkd7dhb5aihr";
    };
    phases = [ "installPhase" ];
    installPhase = ''
      mkdir -p $out/bin
      chmod +x $src && mv $src $out/bin/imgpkg
    '';
  };

in mkShell {

  name = "env";
  buildInputs = [
    azure-cli
    imgpkg
  ];

}