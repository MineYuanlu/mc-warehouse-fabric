package bid.yuanlu.mcwarehouse.storage;

import org.jetbrains.annotations.Nullable;

public interface DataStorage<T> {

	void save(T data);

	@Nullable T load();

	boolean exists();

	void delete();
}
