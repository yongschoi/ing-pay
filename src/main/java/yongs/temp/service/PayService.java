package yongs.temp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.Acknowledgment;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;
import yongs.temp.dao.PayRepository;
import yongs.temp.vo.Order;

@Slf4j
@Service
public class PayService {
	private static final String ORDER_PAY_CREATE = "order-pay-create";
	private static final String PAY_DELIVERY_CREATE = "pay-delivery-create";

	private static final String ORDER_CREATE_ROLLBACK = "order-create-rollback";
	private static final String DELIVERY_CREATE_ROLLBACK = "delivery-create-rollback";
	
	@Autowired
    PayRepository repo;
	@Autowired
    KafkaTemplate<String, String> kafkaTemplate;
	
	@KafkaListener(topics = ORDER_PAY_CREATE)
	public void create(String orderStr, Acknowledgment ack) {
		ObjectMapper mapper = new ObjectMapper();
		Order order = null;
		try {
			order = mapper.readValue(orderStr, Order.class);
			long no = System.currentTimeMillis();
			order.getPay().setNo(no);
			order.getPay().setOrderNo(order.getNo());			 			
			// 결제 API call
			// ...
			// ...

			// db 저장
			repo.insert(order.getPay());
			// delivery pub
			String _tempStr = mapper.writeValueAsString(order);
			kafkaTemplate.send(PAY_DELIVERY_CREATE, _tempStr);
			// mongo는 단일 doc에 대해서 TX를 지원하지 않으므로 DB저장은 마지막에 수행
			log.debug("[PAY Complete] Pay No[" + order.getPay().getNo() + "] / Order No[" + order.getNo() + "]");
		} catch (Exception e) {
			// order 초기화 시 db작업이 없으므로 rollback topic을 하지 않음
			log.debug("[PAY Fail] Order No[" + order.getNo() + "]");
		}
		// 성공하든 실패하든 상관없이
		ack.acknowledge();
	}

	@KafkaListener(topics = {ORDER_CREATE_ROLLBACK, 
							 DELIVERY_CREATE_ROLLBACK})
	public void rollback(String orderStr, Acknowledgment ack) {
		ObjectMapper mapper = new ObjectMapper();
		try {
			Order order = mapper.readValue(orderStr, Order.class);
			repo.deleteByOrderNo(order.getNo());
			ack.acknowledge();
			log.debug("[PAY Rollback] Pay No[" + order.getPay().getNo() + "] / Order No[" + order.getNo() + "]");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
