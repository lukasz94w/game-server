# game-server

## Overwiew:
Repository contains code of several microservices which brought together form a backend part of the game appllication. It's frontend (client) part is available at https://github.com/lukasz94w/game-client.

Project contains of following microservices:
- api-gateway-service which is a entry point for the system, it is responsible i.e. for logging and filtering out the upcoming traffic and forwarding it to the target services,
- auth-service, using cookie and session based architecture for authenticating incoming requests, 
- eureka-server - component storing addresses of the services in the local network,
- gamer-server-core, main part of the application using WebSocket technology to pair the players and transfer the game (TicTacToe) data in between,
- history-service - as name sugests it saves/allows to read data of already finished games.

The structure is complemented by two databases:
- Redis - consumed by auth-service to store the session data,
- MariaDB - for storing the data of games played.

## Used technologies:
- Spring Boot
- Spring Security
- Spring Cloud Netflix
- Spring Cloud Gateway
- Spring Session
- WebSocket
- MariaDB
- Redis
- Maven
- Lombok
- Logback
- Docker

## Structure of the application:
Image below shows simplified scheme of the application:

![scheme](https://github.com/lukasz94w/game-server/assets/53697813/cdc8baa9-b92c-40dc-a63e-212f8126cd2c)

## Running the project:
The quickest way to run the multi-container application leads through docker-compose command:

```
docker-compose up -d
```

After start-up of the application api-gateway-service is available on port 8081. There is also eureka-server port (8761) exposed. Rest of the services are part of internal docker network and are only accessible through 
api-gateway-service. Thanks to the volume usage active sessions and historic games data can survive containers restarts.

It's also possible to run the application parts separately since most of them are Spring Boot applications: 
```
./mvnw spring-boot:run
```

Rest of the components (Redis and MariaDB) can be provided for example by local installations or in separate docker containers. For example to run local MariaDB instance:
```
docker run -d \
  --name local-history-service-mariadb \
  -p 3306:3306 \
  -e MARIADB_DATABASE=database \
  -e MARIADB_USER=root \
  -e MARIADB_PASSWORD=root \
  -e MARIADB_ROOT_PASSWORD=root \
  -v "$(pwd)/local-history-service-mariadb/history-data:/var/lib/mysql" \
  mariadb:11.4.1-rc
```

Regardless of the chosen way it's recommended to wait at least 60 seconds before testing the application to let all of the services register in eureka-server.

## Improvements:
There's a lot of improvements/things to consider which can be applied to the application:

### auth-service:
- registration via email and features like remember me or password reset via email,
- introducton of different account types (admin, user),
- changing the authentication type from cookie-based to JWT,

### eureka-server:
- secure access to the service (currently it's not protected by any password),

### game-server-core:
- consider using JsonMapper to decode/encode messages payload,
- game tracking features like detecting inactive player, maximum game time etc,
- since WebSocket technology is used it should be possible to implement real-time multiplayer game based on server-tick,
- better usage of handleTransportError hook (currently it's only used to log such situations) like applying retry policy when for instance message didn't reach the target user,
- consider using already existing exeption classes like IllegalArgumentException whenever it's possible instead of creating the custom ones,

### history-service:
- games pagination,
- usage of Liquibase to better control the database content (remember to change the "spring.jpa.hibernate.ddl-auto" strategy to "validate" then),
- listing the best players (high-score),

### security:
- implement HTTPS communication which prevents from eavesdropping vulnerable data like user password or cookie,

### docker:
- use docker layers and alpine-based images,

### logging:
- use Elastic Saerch, Zipkin and similar libraries to log/trace internal communication between services,

### overall:
- consider using Kubernetes and other technologies like Grafana, Prometheus, Spring Sleuth, circuit breaker,
- add tests of the application components,
- except from testing itself consider applying pipelines started f.e. when there is a new commit.
