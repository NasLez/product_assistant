FROM nginx:stable-alpine

COPY deploy/nginx/default.conf /etc/nginx/conf.d/default.conf
COPY frontend/dist/ /usr/share/nginx/html/

EXPOSE 80

