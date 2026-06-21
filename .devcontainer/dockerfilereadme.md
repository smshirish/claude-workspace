# Custom docker image

Created custom docker image so as to avoid tool installtion on dev container start up.

Published this image to ghcr 

### pat ghcr
##Github_PAT##

## test token

### Login to WSL machine to access linux commands

- podman machine ssh 
 
- login 
echo "##Github_PAT##" | podman login ghcr.io -u smshirish --password-stdin

- tag image for remote repo
podman tag localhost/my-custom-devcontainer:latest ghcr.io/smshirish/my-custom-devcontainer:latest

- publish image 
podman push ghcr.io/smshirish/my-custom-devcontainer:latest