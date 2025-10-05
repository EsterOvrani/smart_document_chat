FROM node:18-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .

# Build production
RUN npm run build

FROM nginx:alpine

# העתק את ה-build
COPY --from=build /app/build /usr/share/nginx/html

# העתק nginx config מותאם
COPY nginx-frontend.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000

CMD ["nginx", "-g", "daemon off;"]