FROM postman/newman:alpine

# יצירת תיקיות בקונטיינר
RUN mkdir -p /etc/newman/collections /etc/newman/environments /etc/newman/data

# העתקת הקבצים מהתיקיות הקיימות
COPY newman/collections/smart-doc-chat.postman_collection.json /etc/newman/collections/
COPY newman/environments/test.postman_environment.json /etc/newman/environments/
COPY newman/data/EsterOvrani.pdf /etc/newman/data/

# הגדרת working directory
WORKDIR /etc/newman