package expence_tracker.backend.user;

import expence_tracker.backend.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

// JpaRepository<Entity, типКлюча> даёт save/findById/findAll/delete «из коробки».
public interface UserRepository extends JpaRepository<User, Long> {
    /*
    * В JPQL обращаемся к имени сущности (User) и её полям (u.email),
    * а не к именам таблицы/колонок БД. @Param("email") связывает аргумент метода с :email в запросе.
    * */
    // COUNT(...) > 0 — проверка существования: считаем строки с таким email.
    @Query("""
        SELECT COUNT(u) > 0 
        FROM User u
        WHERE u.email = :email
        """)
    boolean existsByEmail(@Param("email") String email);

    @Query("""
    SELECT  u
    FROM User u
    WHERE u.email = :email
    """)
    Optional<User> findByEmail(@Param("email") String email);
}
