package yongs.temp.dao;

import org.springframework.data.mongodb.repository.MongoRepository;

import yongs.temp.model.Pay;

public interface PayRepository extends MongoRepository<Pay, String> {
	public void deleteByOrderNo(final long orderNo);
}
