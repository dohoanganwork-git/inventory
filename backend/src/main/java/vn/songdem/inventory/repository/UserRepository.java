package vn.songdem.inventory.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.songdem.inventory.domain.Role;
import vn.songdem.inventory.domain.User;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, String> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByUsernameIgnoreCase(String username);
    long countByRoleAndActiveTrue(Role role);
    List<User> findByRoleInAndActiveTrue(Collection<Role> roles);
    List<User> findAllByOrderByCreatedAtAsc();
}
