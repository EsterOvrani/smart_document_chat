FROM node:18-alpine AS build
WORKDIR /app

# ⭐ הגדרת build argument
ARG REACT_APP_GOOGLE_CLIENT_ID

# ⭐ הפיכתו למשתנה סביבה בזמן build
ENV REACT_APP_GOOGLE_CLIENT_ID=$REACT_APP_GOOGLE_CLIENT_ID

COPY package*.json ./
RUN npm ci
COPY . .

# עכשיו npm run build יכלול את המשתנה!
RUN npm run build

FROM nginx:alpine
COPY --from=build /app/build /usr/share/nginx/html
COPY nginx-frontend.conf /etc/nginx/conf.d/default.conf

EXPOSE 3000
CMD ["nginx", "-g", "daemon off;"]