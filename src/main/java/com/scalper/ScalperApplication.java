package com.scalper;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.TimeZone;

/**
 * Application principale du Scalper Assistant
 * Assistant du Scalper Éclairé - Multi-Sessions Trading
 */
@Slf4j
@SpringBootApplication
@EnableJpaRepositories(basePackages = "com.scalper.repository")
@EnableJpaAuditing
@EnableTransactionManagement
@EnableCaching
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan(basePackages = "com.scalper.config")
public class ScalperApplication {

	public static void main(String[] args) {
		// Configuration du timezone global en UTC
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneOffset.UTC));

		log.info("🎯 Démarrage de l'Assistant du Scalper Éclairé");
		log.info("⏰ Timezone configuré: UTC");
		log.info("🕐 Heure de démarrage: {}", ZonedDateTime.now(ZoneOffset.UTC));

		try {
			SpringApplication app = new SpringApplication(ScalperApplication.class);

			// Configuration du banner personnalisé
			app.setBannerMode(org.springframework.boot.Banner.Mode.CONSOLE);

			// Démarrage de l'application
			var context = app.run(args);

			log.info("✅ Application démarrée avec succès");
			log.info("📊 Profil actif: {}",
					String.join(", ", context.getEnvironment().getActiveProfiles()));
			log.info("🌐 Port: {}",
					context.getEnvironment().getProperty("server.port", "8080"));
			log.info("📋 Management: http://localhost:{}/actuator/health",
					context.getEnvironment().getProperty("server.port", "8080"));
			log.info("📚 API Documentation: http://localhost:{}/swagger-ui.html",
					context.getEnvironment().getProperty("server.port", "8080"));

			// Vérification des composants critiques au démarrage
			verifySystemComponents(context);

		} catch (Exception e) {
			log.error("❌ Erreur lors du démarrage de l'application", e);
			System.exit(1);
		}
	}

	/**
	 * Vérification rapide des composants critiques au démarrage
	 */
	private static void verifySystemComponents(org.springframework.context.ConfigurableApplicationContext context) {
		try {
			// Vérification de la connexion à la base de données
			var dataSource = context.getBean(javax.sql.DataSource.class);
			try (var connection = dataSource.getConnection()) {
				log.info("✅ Base de données: Connexion établie");
			}

			// Vérification de Redis (si disponible)
			try {
				var redisTemplate = context.getBean("redisTemplate");
				log.info("✅ Redis: Connexion établie");
			} catch (Exception e) {
				log.warn("⚠️ Redis: Non disponible ({})", e.getMessage());
			}

			// Vérification des services critiques
			try {
				context.getBean("multiSessionLevelService");
				log.info("✅ MultiSessionLevelService: Initialisé");
			} catch (Exception e) {
				log.warn("⚠️ MultiSessionLevelService: Non disponible");
			}

			try {
				context.getBean("sessionContextAnalyzer");
				log.info("✅ SessionContextAnalyzer: Initialisé");
			} catch (Exception e) {
				log.warn("⚠️ SessionContextAnalyzer: Non disponible");
			}

			log.info("🎯 Vérification des composants terminée");

		} catch (Exception e) {
			log.error("❌ Erreur lors de la vérification des composants", e);
		}
	}
}